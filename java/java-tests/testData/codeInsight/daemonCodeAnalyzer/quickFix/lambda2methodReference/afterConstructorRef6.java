// "Replace lambda with method reference" "true-preview"
class NonStaticInner3 {
    class Foo {
    }

    interface I1<X> {
        X m();
    }

    {
        I1<Foo> b2 = Foo::new;
    }
}