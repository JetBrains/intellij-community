import java.util.AbstractSet;
import java.util.Set;

class Expr {
	private Class<? super AbstractSet[]> myField;
	public void meth() {
		myField = int.class;
		myField = int[].class;
		myField = Set.class;
		myField = Set[].class;
		myField = void.class;
	}
}
