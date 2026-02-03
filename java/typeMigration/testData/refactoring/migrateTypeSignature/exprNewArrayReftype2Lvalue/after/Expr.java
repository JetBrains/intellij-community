interface FaceParent {}
interface FaceChild extends FaceParent {}
class ClassParent implements FaceChild {}
class ClassChild extends ClassParent {}

class Expr {
	private FaceParent[] myArrayOne;
	private FaceParent[][] myArrayTwo;
	public void meth(FaceParent pfc, ClassParent pcp, ClassChild pcc) {
		myArrayOne = new FaceParent[]{pfc, pcp, pcc};
		myArrayTwo = new FaceParent[][]{{pfc}, {pcp}, {pcc}};
	}
}
