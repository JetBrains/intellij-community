// "Add import for 'test.Bar.Foo'" "true"
package test;

class Bar {
    public static @interface Foo {}
}
@Bar.<caret>Foo
public class OOO {
}