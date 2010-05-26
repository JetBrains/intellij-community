public class Foo {

	public static void bazz(int i) {
		final Foo foo = i != 0 ? <selection>new Foo(new IBar() {
			public void doSomething() {
				System.out.println("hello");
			}
		})</selection> : new Foo(new IBar() {
			public void doSomething() {
				System.out.println("hello");
			}
		});
		foo.bla();
	}

	private final IBar bar;

	public Foo(IBar bar) {
		this.bar = bar;
	}

	public void bla() {
		bar.doSomething();
	}

	public interface IBar {
		void doSomething();
	}
}