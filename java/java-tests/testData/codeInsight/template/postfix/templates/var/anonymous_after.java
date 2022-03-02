public class Foo {
    void m() {
        Runnable r = new Runnable() {
            public void run()
        }; foo(r);
    }

    void foo(Runnable r) {}
}