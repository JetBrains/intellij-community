interface Foo {}
interface FooEx<T> extends Foo {
    T foo();
}

class Bar {
    {
        Foo f = new FooEx<<caret>>() {};
    }
}