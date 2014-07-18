class ClassParent {}
class ClassChild extends ClassParent {}
class ClassGrandChild extends ClassChild {}

class Ession {
	public ClassParent myForSuperAccess;
	public ClassParent forSuperAccess() {
		return myForSuperAccess;
	}
}

class Expr extends Ession {
	public void methMemAcc() {
		ClassParent vfsuper = super.myForSuperAccess;
		ClassParent vmsuper = super.forSuperAccess();

		ClassParent vfcsuper = Expr.super.myForSuperAccess;
		ClassParent vmcsuper = Expr.super.forSuperAccess();
	}
}
