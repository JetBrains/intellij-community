interface Foo {}

class Bar {
    void foo(Foo... f)

    {
        foo(new <caret>)
    }
}
