// "Add static import for 'test.Bar.f'" "false"
package test;

import static test.Bar1.f;

class Bar {
    public static final void f() {}
}

class Bar1 {
    public static final void f() {}
}
public class Foo {
    {
        Bar.<caret>f();
    }
}