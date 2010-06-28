// "Add on demand static import for 'test.Bar'" "true"
package test;

class Bar {
    public static final void f() {}
}
public class Foo {
    public static final void f(int i) {}

    {
        <caret>Bar.f();   // invoke 'add on demand static import' for Bar class here. The call is now done to other method.
    }
}