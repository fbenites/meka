/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package meka.classifiers.multilabel;


import meka.core.OptionUtils;
import org.kramerlab.bmad.algorithms.BooleanMatrixDecomposition;
import org.kramerlab.bmad.general.Tuple;
import org.kramerlab.bmad.matrix.BooleanMatrix;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.TechnicalInformation;
import weka.core.TechnicalInformation.Field;
import weka.core.TechnicalInformation.Type;
import weka.core.TechnicalInformationHandler;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

/**
 * MLC-BMaD - Multi-Label Classification using Boolean Matrix Decomposition. Transforms 
 * the labels using a Boolean matrix decomposition, the first resulting matrix are 
 * used as latent labels and a classifier is trained to predict them. The second matrix is 
 * used in a multiplication to decompress the predicted latent labels. 
 * <br>
 * See: J&ouml;rg Wicker, Bernhard Pfahringer, Stefan Kramer. <i>Multi-label Classification Using Boolean Matrix Decomposition</i>. Proceedings of the 27th Annual ACM Symposium on Applied Computing, pp. 179–186, ACM, 2012.
 *
 * @author 	Joerg Wicker (wicker@uni-mainz.de)
 */
public class MLCBMaD extends LabelTransformationClassifier implements TechnicalInformationHandler {

	protected static final long serialVersionUID = 585507197229071545L;

	protected Instances uppermatrix = null;

	protected Instances compressedMatrix = null;

	protected int size = getDefaultSize();

	protected double threshold = getDefaultThreshold();

	protected double getDefaultThreshold(){
		return 0.5;
	}

	protected int getDefaultSize(){
		return 5;
	}

	public int getSize(){
		return size;
	}

	public void setSize(int size){
		this.size = size;
	}

	public String sizeTipText(){
		return "Size of the compressed matrix. Should be \n"
			+ "less than the number of labels and more than 1.";
	}

	public double getThreshold(){
		return threshold;
	}


	public void setThreshold(double threshold){
		this.threshold = threshold;
	}

	public String thresholdTipText(){
		return "Threshold for the matrix decompositon, what is considered frequent."
			+ "\n Between 0 and 1.";
	}

	public String globalInfo() {
		return
			"MLC-BMaD - Multi-Label Classification using Boolean Matrix Decomposition. Transforms "
				+ "the labels using a Boolean matrix decomposition, the first resulting matrix are "
				+ "used as latent labels and a classifier is trained to predict them. The second matrix is "
				+ "used in a multiplication to decompress the predicted latent labels.\n"
				+ "For more information see:\n"
				+ getTechnicalInformation();
	}

	public Enumeration listOptions() {
		Vector newVector = new Vector();

		OptionUtils.addOption(newVector,
			sizeTipText(),
			""+getDefaultSize(),
			"size");

		OptionUtils.addOption(newVector,
			thresholdTipText(),
			""+getDefaultThreshold(),
			"threshold");

		OptionUtils.add(newVector, super.listOptions());

		return OptionUtils.toEnumeration(newVector);
	}

	public String[] getOptions(){
		List<String> result = new ArrayList<>();
		OptionUtils.add(result, "size", getSize());
		OptionUtils.add(result, "threshold", getThreshold());
		OptionUtils.add(result, super.getOptions());
		return OptionUtils.toArray(result);
	}

	public void setOptions(String[] options) throws Exception {
		setSize(OptionUtils.parse(options, "size", getDefaultSize()));
		setThreshold(OptionUtils.parse(options, "threshold", getDefaultThreshold()));
		super.setOptions(options);
	}

	@Override
	public TechnicalInformation getTechnicalInformation() {
		TechnicalInformation	result;

		result = new TechnicalInformation(Type.INPROCEEDINGS);
		result.setValue(Field.AUTHOR, "J\"org Wicker, Bernhard Pfahringer, Stefan Kramer");
		result.setValue(Field.TITLE, "Multi-Label Classification using Boolean Matrix Decomposition");
		result.setValue(Field.BOOKTITLE, "Proceedings of the 27th Annual ACM Symposium on Applied Computing");
		result.setValue(Field.YEAR, "2011");
		result.setValue(Field.PAGES, "179-186");

		return result;
	}

	@Override
	public Instance transformInstance(Instance x) throws Exception{
		Instances tmpInst = new Instances(x.dataset());

		tmpInst.delete();
		tmpInst.add(x);

		Instances features = this.extractPart(tmpInst, false);

		Instances pseudoLabels = new Instances(this.compressedMatrix);
		Instance tmpin = pseudoLabels.instance(0);
		pseudoLabels.delete();

		pseudoLabels.add(tmpin);

		for ( int i = 0; i< pseudoLabels.classIndex(); i++) {
			pseudoLabels.instance(0).setMissing(i);
		}

		Instances newDataSet = Instances.mergeInstances(pseudoLabels, features);
		newDataSet.setClassIndex(this.size);

		return newDataSet.instance(0);
	}

	@Override
	public Instances transformLabels(Instances D) throws Exception{

		Instances features = this.extractPart(D, false);
		Instances labels = this.extractPart(D, true);

		BooleanMatrixDecomposition bmd =
			BooleanMatrixDecomposition.BEST_CONFIGURED(this.threshold);
		Tuple<Instances, Instances> res = bmd.decompose(D, this.size);

		this.compressedMatrix = res._1;
		this.uppermatrix = res._2;

		Instances result= Instances.mergeInstances(compressedMatrix,
			features);
		result.setClassIndex(this.size);
		return result;
	}

	@Override
	public double[] transformPredictionsBack(double[] y){
		byte[] yByteArray = new byte[y.length];

		for(int i = 0; i < y.length; i++){
			yByteArray[i] = y[i]>0.5 ? BooleanMatrix.TRUE:BooleanMatrix.FALSE;
		}

		BooleanMatrix yMatrix =
			new BooleanMatrix( new byte[][]{yByteArray});
		BooleanMatrix reconstruction =
			yMatrix.booleanProduct(new BooleanMatrix(this.uppermatrix));

		double[] result = new double[y.length];

		for(int i = 0; i < y.length; i++){
			result[i] = reconstruction.apply(0,i) == BooleanMatrix.TRUE  ? 1.0:0.0;
		}

		return result;
	}

	@Override
	public String getModel(){
		return "";
	}

	@Override
	public String toString() {
		return getModel();
	}

	/**
	 * Main method for testing.
	 * @param args - Arguments passed from the command line
	 **/
	public static void main(String[] args) throws Exception{
		AbstractMultiLabelClassifier.evaluation(new MLCBMaD(), args);
	}
}