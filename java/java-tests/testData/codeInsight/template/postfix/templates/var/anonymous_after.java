public class Foo {
    void m() {
        Runnable foo = new Runnable() {
            public void run()
        }; foo(foo);
    }

    void foo(Runnable r) {}
}