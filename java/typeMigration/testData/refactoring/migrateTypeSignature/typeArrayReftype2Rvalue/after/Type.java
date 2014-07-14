interface Ancestor {}
interface Subject extends Ancestor {}
class Descendant implements Subject {}

class Type {
	private Subject[][] myField;
	public void meth(Subject[][] pa, Subject[][] ps, Descendant[][] pd) {
		myField = pa;
		myField = ps;
		myField = pd;
	}
}
