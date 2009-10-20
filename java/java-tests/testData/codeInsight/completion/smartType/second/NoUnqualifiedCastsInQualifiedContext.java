class C{
    {
        Object o;
        if (o instanceof Foo) {
            new Object().do<caret>
        }
    }
}

class Foo {
    void dofoo();
}