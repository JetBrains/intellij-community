interface FaceParent {}
interface FaceChild extends FaceParent {}

class Expr {
	private FaceParent[][] myArrayOne;
	private FaceParent[][][] myArrayTwo;
	public void meth(FaceParent[] pfc) {
		myArrayOne = new FaceParent[][]{pfc};
		myArrayTwo = new FaceParent[][][]{{pfc}};
	}
}
