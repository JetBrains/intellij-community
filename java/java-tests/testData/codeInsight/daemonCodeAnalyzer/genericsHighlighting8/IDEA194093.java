import java.util.List;

class Repro { // signatures from org.hamcrest, ExactlyOneOf is custom

	interface Matcher<T> { }

	public static <T> Matcher<Iterable<? super T>> hasItem(T item) { return null; }

	@SafeVarargs
	public static <E> Matcher<Iterable<? extends E>> contains(E... items) { return null; }

	class ExactlyOneOf<T> implements Matcher<T> { }

	@SafeVarargs
	public static <T> ExactlyOneOf<T> exactlyOneOf(Matcher<? super T>... matchers) { return null; }

	void repro() {
		Matcher<List<String>> matcher = exactlyOneOf<error descr="'exactlyOneOf(Repro.Matcher<? super java.util.List<java.lang.String>>...)' in 'Repro' cannot be applied to '(Repro.Matcher<java.lang.Iterable<? super java.lang.String>>, Repro.Matcher<java.lang.Iterable<? super java.lang.String>>, Repro.Matcher<java.lang.Iterable<? extends java.lang.String>>)'">( // not red or yellow
				hasItem("hello"),
				hasItem("world"),
				contains("a", "b")
		)</error>;
	}
}