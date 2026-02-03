class Expr {
	private short myField;
	public void meth() {
		myField = null;

		myField = false;
		myField = true;

		myField = 043;
		myField = 35;
		myField = 0x23;

		myField = '#';

		myField = 043L;
		myField = 35L;
		myField = 0x23L;

		myField = 043F;
		myField = 35F;
		myField = 0x23F;

		myField = 35.0;

		myField = "#";
	}
}
