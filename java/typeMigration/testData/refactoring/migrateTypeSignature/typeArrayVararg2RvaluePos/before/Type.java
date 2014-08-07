interface Ancestor {}
interface Subject extends Ancestor {}
class Descendant implements Subject {}

class Type {
	private Ancestor[] myField;
	public void meth(Subject... p) {
		myField = p;
	}
}
