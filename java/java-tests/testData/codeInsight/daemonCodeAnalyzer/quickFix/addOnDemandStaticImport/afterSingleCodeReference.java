// "Add static import for 'test.Bar.Foo'" "true"
package test;

import static test.Bar.Foo;

class Bar {
    public static class Foo {}
}
public class OOO{
    {
        Foo foo;
    }
}