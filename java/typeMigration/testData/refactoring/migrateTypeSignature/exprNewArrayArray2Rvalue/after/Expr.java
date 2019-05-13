interface FaceParent {}
interface FaceChild extends FaceParent {}
class ClassParent implements FaceChild {}
class ClassChild extends ClassParent {}

class Expr {
	private ClassParent[][][] myField;
	public void meth(ClassParent[] pfc) {
		myField = new ClassParent[][][]{{pfc}, {null}, null};
	}
}
