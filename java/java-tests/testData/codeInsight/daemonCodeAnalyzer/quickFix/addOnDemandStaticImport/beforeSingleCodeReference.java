// "Add import for 'test.Bar.Foo'" "true"
package test;

class Bar {
    public static class Foo {}
}
public class OOO{
    {
        Bar.<caret>Foo foo;
    }
}