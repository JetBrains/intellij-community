interface Foo {}
class FooEx<T> implements Foo {}

class Bar {
    {
        Foo f = new FE<caret>
    }
}