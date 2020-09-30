
import java.util.function.Function;

class Main {

	void m(B b) {
		Function<A<String>, String> f1 = A<String>::getName;
		Function<A<String>, String> f10 = A::getName;
		Function<B, String> f2 = B::getName;
		Function<B, String> f3 = b::<error descr="Reference to 'getName' is ambiguous, both 'getName()' and 'getName(I)' match">getName</error>;
	}
}

interface I {
	String getName();

	static String getName(final I i) {
		return null;
	}
}
class A<T> implements I {
	@Override
	public String getName() {
		return null;
	}
}

class B implements I {
	@Override
	public String getName() {
		return null;
	}
}
