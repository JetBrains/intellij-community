import java.util.Set;

class Expr {
	private Object myField;
	public void meth() {
		myField = int.class;
		myField = int[].class;
		myField = Set.class;
		myField = Set[].class;
		myField = void.class;
	}
}
