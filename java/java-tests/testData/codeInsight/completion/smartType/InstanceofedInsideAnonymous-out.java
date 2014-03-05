class C{
    {
        final Object o;
        if (o instanceof Foo) {
            new Runnable() {
                public void run() {
                    Foo f = (Foo) o;<caret>
                }
            }
        }
    }
}

class Foo {
    void dofoo();
}