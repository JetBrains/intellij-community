class Foo {
    <T> Foo(T t) {

    }

    static {
        Foo f = <String>new <ref>Foo();
    }
}