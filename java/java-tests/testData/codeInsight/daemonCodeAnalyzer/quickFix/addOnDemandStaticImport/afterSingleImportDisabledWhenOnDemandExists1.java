// "Add static import for 'test.Bar.f'" "true"
package test;

import static test.Bar.*;

class Bar {
    public static final void f() {}
}
public class Foo {
    {
        f();
    }
}