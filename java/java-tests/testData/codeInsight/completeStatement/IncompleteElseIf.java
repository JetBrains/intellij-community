class Foo {
    boolean a;
    {
        if (a) {
        } el<caret>se
        a = 2;
    }
}