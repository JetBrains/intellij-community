// "Add on demand static import for 'test.Bar'" "true"
package test;

import static test.Bar.*;

class Bar {
    public static final void f(String s) {}
    public static final void f(int i) {}
}
public class Foo {
    {
        <caret>f();
    }
}