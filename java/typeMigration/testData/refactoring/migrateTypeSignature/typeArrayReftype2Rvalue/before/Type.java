interface Ancestor {}
interface Subject extends Ancestor {}
class Descendant implements Subject {}

class Type {
	private Ancestor[][] myField;
	public void meth(Ancestor[][] pa, Subject[][] ps, Descendant[][] pd) {
		myField = pa;
		myField = ps;
		myField = pd;
	}
}
