class Foo {
    boolean a;
    {
        if (a) {
        } else {
            <caret>
        }
        a = 2;
    }
}