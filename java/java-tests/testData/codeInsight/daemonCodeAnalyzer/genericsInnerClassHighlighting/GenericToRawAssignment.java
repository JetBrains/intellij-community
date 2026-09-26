class Outer<T> {
    class Inner { }
    Foo<Outer.Inner> m(Foo<Outer<Integer>.Inner> foo) {
        return <error descr="Incompatible types. Found: 'Foo<Outer<java.lang.Integer>.Inner>', required: 'Foo<Outer.Inner>'">foo</error>;
    }
}
class Foo<X> {}