// "Add import for 'test.Bar.Foo'" "true-preview"
package test;

class Bar {
    public static class Foo {}
}
public class OOO{
    {
        Bar.<caret>Foo foo;
    }
}