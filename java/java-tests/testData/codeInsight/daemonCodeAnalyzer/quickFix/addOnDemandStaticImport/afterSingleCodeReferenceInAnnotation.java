// "Add import for 'test.Bar.Foo'" "true-preview"
package test;

import test.Bar.Foo;

class Bar {
    public static @interface Foo {}
}
@Foo
public class OOO {
}