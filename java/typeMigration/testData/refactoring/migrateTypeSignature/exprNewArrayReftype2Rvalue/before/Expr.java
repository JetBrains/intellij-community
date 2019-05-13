interface FaceParent {}
interface FaceChild extends FaceParent {}
class ClassParent implements FaceChild {}
class ClassChild extends ClassParent {}

class Expr {
	private FaceChild[][] myField;
	public void meth(FaceChild pfc, ClassParent pcp, ClassChild pcc) {
		myField = new FaceChild[][]{{pfc}, {pcp}, {pcc}, {null}};
	}
}
