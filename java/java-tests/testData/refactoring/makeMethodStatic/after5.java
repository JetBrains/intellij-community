public class Foo {
    public int i;
    public static int <caret>method(Foo anObject) {
        return anObject.i;
    }
}