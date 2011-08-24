// "Add static import for 'test.Bar.f'" "false"
package test;

import static test.Bar.*;

class Bar {
    public static final void f() {}
}
public class Foo extends FooSuper{
    {
        Bar.<caret>f();
    }
}

class FooSuper {
  public static final void f(int i) {}
}