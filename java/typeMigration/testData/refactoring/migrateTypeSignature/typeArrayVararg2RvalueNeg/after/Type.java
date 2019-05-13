interface Ancestor {}
interface Subject extends Ancestor {}
class Descendant implements Subject {}

class Type {
	private Descendant[] myField;
	public void meth(Descendant[] p) {
		myField = p;
	}
}
