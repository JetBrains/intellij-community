interface Ancestor {}
interface Subject extends Ancestor {}
class Descendant implements Subject {}

class Type {
	private Subject[] myField;
	public void meth(Subject... p) {
		myField = p;
	}
}
