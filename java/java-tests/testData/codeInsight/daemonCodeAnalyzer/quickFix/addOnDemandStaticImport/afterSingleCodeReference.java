// "Add import for 'test.Bar.Foo'" "true"
package test;

import test.Bar.Foo;

class Bar {
    public static class Foo {}
}
public class OOO{
    {
        Foo foo;
    }
}