// "Add on demand static import for 'test.Bar'" "true"
package test;

class Bar {
    public static final void f(String s) {}
    public static final void f(int i) {}
}
public class Foo {
    {
        <caret>Bar.f();
    }
}