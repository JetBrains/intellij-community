// "Add static import for 'test.Bar.f'" "true"
package test;

import static test.Bar.f;

class Bar {
    public static final void f() {}
}
public class Foo {
    public static final void f(int i) {}
    {
        Bar.<caret>f();
    }
}