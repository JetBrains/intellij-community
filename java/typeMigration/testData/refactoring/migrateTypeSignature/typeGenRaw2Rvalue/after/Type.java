import java.util.Set;

class Any {}

class Type {
	private Set myField;
	public void meth(Set<Any> p) {
		myField = p;
	}
}
