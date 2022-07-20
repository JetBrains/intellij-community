// "Add import for 'test.Bar.Foo'" "true-preview"
package test;

class Bar {
    public static @interface Foo {}
}
@Bar.<caret>Foo
public class OOO {
}