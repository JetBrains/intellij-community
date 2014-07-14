class ClassParent {}
class ClassChild extends ClassParent {
	public void forAccess2() {
	}

	public int myForAccess;
}
class ClassGrandChild extends ClassChild {}

class Expr {
	public ClassParent myForAccess;
	public ClassParent forAccess() {
		return myForAccess;
	}

	public void methMemAcc() {
		ClassParent vf = myForAccess;
		ClassParent vm = forAccess();

		ClassParent vfthis = this.myForAccess;
		ClassParent vmthis = this.forAccess();

		ClassParent vfcthis = Expr.this.myForAccess;
		ClassParent vmcthis = Expr.this.forAccess();

		ClassParent vfparen = (this).myForAccess;
		ClassParent vmparen = (this).forAccess();

		ClassParent vfnew = new Expr().myForAccess;
		ClassParent vmnew = new Expr().forAccess();

		int v = forAccess().myForAccess;
		forAccess().forAccess2();
	}
}
