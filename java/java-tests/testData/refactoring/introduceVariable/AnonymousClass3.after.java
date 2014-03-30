import java.io.PrintStream;
class Foo {

    public static void bazz(int i) {
        Foo j = new Foo(new IBar() {
            public void doSomething(PrintStream out) {
                out.println("hello");
            }
        });
        final Foo foo = i != 0 ? j : j;
        foo.bla();
    }

    private final IBar bar;

    public Foo(IBar bar) {
        this.bar = bar;
    }

    public void bla() {
        bar.doSomething(System.out);
    }

    public interface IBar {
        void doSomething(PrintStream out);
    }
}