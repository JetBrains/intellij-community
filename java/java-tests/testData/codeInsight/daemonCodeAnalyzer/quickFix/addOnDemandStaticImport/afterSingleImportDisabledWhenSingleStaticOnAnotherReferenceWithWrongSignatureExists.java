// "Add static import for 'test.Bar.f'" "true"
package test;

import static test.Bar.f;
import static test.Bar1.f;

class Bar {
    public static final void f() {}
}

class Bar1 {
    public static final void f(int i) {}
}
public class Foo {
    {
        f();
    }
}