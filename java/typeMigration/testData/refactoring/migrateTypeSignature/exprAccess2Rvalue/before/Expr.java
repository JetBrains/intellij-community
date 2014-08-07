class ClassParent {}
class ClassChild extends ClassParent {
	public void forAccess2() {
	}

	public int myForAccess;
}
class ClassGrandChild extends ClassChild {}

class Expr {
	private ClassChild myField;

	public ClassChild myForAccess1;
	public ClassChild forAccess1() {
		return null;
	}

	public ClassChild myForAccess2;
	public ClassChild forAccess2() {
		return null;
	}

	public ClassChild myForAccess3;
	public ClassChild forAccess3() {
		return null;
	}

	public ClassChild myForAccess4;
	public ClassChild forAccess4() {
		return null;
	}

	public ClassChild myForAccess5;
	public ClassChild forAccess5() {
		return null;
	}

	public void methMemAcc() {
		myField = myForAccess1;
		myField = forAccess1();

		myField = this.myForAccess2;
		myField = this.forAccess2();

		myField = Expr.this.myForAccess3;
		myField = Expr.this.forAccess3();

		myField = (this).myForAccess4;
		myField = (this).forAccess4();

		myField = new Expr().myForAccess5;
		myField = new Expr().forAccess5();
	}
}
