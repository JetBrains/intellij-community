interface FaceParent {}
interface FaceChild extends FaceParent {}
class ClassParent implements FaceChild {}
class ClassChild extends ClassParent {}

class Type {
	private ClassParent myField;
	public void meth(ClassChild pcc, ClassParent pcp, ClassParent pfc, ClassParent pfp) {
		myField = pcc;
		myField = pcp;
		myField = pfc;
		myField = pfp;
	}
}
