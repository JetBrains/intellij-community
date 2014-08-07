class ClassParent {}
class ClassChild extends ClassParent {
	public void forAccess2() {
	}

	public int myForAccess;
}
class ClassGrandChild extends ClassChild {}

class Expr {
	private ClassGrandChild myField;

	public ClassGrandChild myForAccess1;
	public ClassGrandChild forAccess1() {
		return null;
	}

	public ClassGrandChild myForAccess2;
	public ClassGrandChild forAccess2() {
		return null;
	}

	public ClassGrandChild myForAccess3;
	public ClassGrandChild forAccess3() {
		return null;
	}

	public ClassGrandChild myForAccess4;
	public ClassGrandChild forAccess4() {
		return null;
	}

	public ClassGrandChild myForAccess5;
	public ClassGrandChild forAccess5() {
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
