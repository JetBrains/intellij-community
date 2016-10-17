public class Foo {
    void m() {
        foo(new Runnable() {public void run()}.var<caret>);
    }

    void foo(Runnable r) {}
}