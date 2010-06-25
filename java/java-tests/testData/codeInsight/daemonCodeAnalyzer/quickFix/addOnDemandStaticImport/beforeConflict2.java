// "Add on demand static import for 'test.Bar'" "true"
package test;

class Bar {
    public static final void f() {
    }
}

public class Foo {
    {
        Bar.f();   // invoke 'add on demand static import' for Bar class here. The call is now done to other method.
    }

    static class D {
        public static final void f(int i) {
        }

        {
            Bar.f();   // invoke 'add on demand static import' for Bar class here. The call is now done to other method.
        }
    }

    {
        Bar.f();   // invoke 'add on demand static import' for Bar class here. The call is now done to other method.
    }

    {
        Bar.f();   // invoke 'add on demand static import' for Bar class here. The call is now done to other method.
    }

    {
        <caret>Bar.f();   // invoke 'add on demand static import' for Bar class here. The call is now done to other method.
    }
}