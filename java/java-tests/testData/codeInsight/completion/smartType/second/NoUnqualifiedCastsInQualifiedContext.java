class C{
    {
        Object o;
        if (o instanceof Foo) {
            new Object().dofo<caret>
        }
    }
}

class Foo {
    void dofoo();
}