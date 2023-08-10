public class Foo {

    private final int arg;

    public Foo(int arg) {
        this.<caret>arg = arg;
    }
}