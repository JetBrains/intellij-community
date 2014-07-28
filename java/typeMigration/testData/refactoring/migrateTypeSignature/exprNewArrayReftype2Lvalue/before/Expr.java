interface FaceParent {}
interface FaceChild extends FaceParent {}
class ClassParent implements FaceChild {}
class ClassChild extends ClassParent {}

class Expr {
	private FaceChild[] myArrayOne;
	private FaceChild[][] myArrayTwo;
	public void meth(FaceChild pfc, ClassParent pcp, ClassChild pcc) {
		myArrayOne = new FaceChild[]{pfc, pcp, pcc};
		myArrayTwo = new FaceChild[][]{{pfc}, {pcp}, {pcc}};
	}
}
