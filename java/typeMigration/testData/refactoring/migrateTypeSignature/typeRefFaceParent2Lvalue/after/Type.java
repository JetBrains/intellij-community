interface FaceParent {}
interface FaceChild extends FaceParent {}
class ClassParent implements FaceChild {}
class ClassChild extends ClassParent {}

class Type {
	private FaceParent myClassChild;
	private FaceParent myClassParent;
	private FaceParent myFaceChild;
	private FaceParent myFaceParent;

	public void meth(FaceParent p) {
		myClassChild = p;
		myClassParent = p;
		myFaceChild = p;
		myFaceParent = p;
	}
}