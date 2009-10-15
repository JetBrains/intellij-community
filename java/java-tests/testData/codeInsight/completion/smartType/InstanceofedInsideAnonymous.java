class C{
    {
        Object o;
        if (o instanceof Foo) {
            new Runnable() {
                public void run() {
                    Foo f = <caret>
                }
            }
        }
    }
}

class Foo {
    void dofoo();
}