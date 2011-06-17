// "Add static import for 'test.Bar.f'" "true"
package test;

class Bar {
    public static final void f() {}
}
public class Foo {
    public static final void f(int i) {}
    {
        Bar.<caret>f();
    }
}