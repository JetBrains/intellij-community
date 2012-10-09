// "Replace lambda with method reference" "true"
class NonStaticInner3 {
    class Foo {
    }

    interface I1<X> {
        X m();
    }

    {
        I1<Foo> b2 = () -> <caret>new Foo();
    }
}