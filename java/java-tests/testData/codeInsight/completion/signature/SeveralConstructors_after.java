class Foo{
    Foo(int arg) {
    }
    Foo() {
    }
    Foo(boolean arg) {
    }

    {
        Foo f = new Foo();<caret>
    }
}