class Foo {
	public <caret>Foo() {
	}

	public Foo(String text) {
	}
}
class Bar extends Foo {

	public Bar() {
		super("hello");
	}
}