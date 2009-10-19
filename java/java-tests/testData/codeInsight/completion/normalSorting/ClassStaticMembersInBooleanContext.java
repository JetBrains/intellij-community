public class Foo {
    {
        if (Bar.<caret>)
    }
}

class Bar {

    public static void voidMethod() {}
    public static boolean booleanMethod() {}
    public static final boolean BOOLEAN = true;
    public static final Object AN_OBJECT = "";

    public static class Inner {}


}
