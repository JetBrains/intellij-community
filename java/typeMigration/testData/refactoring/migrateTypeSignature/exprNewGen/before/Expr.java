import java.util.Set;

class Ancestor {}
class Subject extends Ancestor {}
class Descendant extends Subject {}

class Expr {
	private Object myField;
	public void meth() {
		myField = new Set();
		myField = new Set<Ancestor>();
		myField = new Set<Subject>();
		myField = new Set<Descendant>();
	}
}
