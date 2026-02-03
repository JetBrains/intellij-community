class ClassParent {}
class ClassChild extends ClassParent {}
class ClassGrandChild extends ClassChild {}

class Ession {
	public ClassChild myForSuperAccess1;
	public ClassChild forSuperAccess1() {
		return null;
	}

	public ClassChild myForSuperAccess2;
	public ClassChild forSuperAccess2() {
		return null;
	}
}

class Expr extends Ession {
	private ClassChild myField;
	public void methMemAcc() {
		myField = super.myForSuperAccess1;
		myField = super.forSuperAccess1();

		myField = Expr.super.myForSuperAccess2;
		myField = Expr.super.forSuperAccess2();
	}
}
