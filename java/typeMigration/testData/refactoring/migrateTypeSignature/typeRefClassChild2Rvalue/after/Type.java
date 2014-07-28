interface FaceParent {}
interface FaceChild extends FaceParent {}
class ClassParent implements FaceChild {}
class ClassChild extends ClassParent {}

class Type {
	private ClassChild myField;
	public void meth(ClassChild pcc, ClassChild pcp, ClassChild pfc, ClassChild pfp) {
		myField = pcc;
		myField = pcp;
		myField = pfc;
		myField = pfp;
	}
}
