public class Foo {

    private Foo arg;

    public void bar(Foo arg) {
        this.<caret>arg = arg;
    }
}