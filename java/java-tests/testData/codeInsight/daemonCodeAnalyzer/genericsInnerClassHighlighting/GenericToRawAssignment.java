class Outer<T> {
    class Inner { }
    Foo<Outer.Inner> <error descr="Invalid return type">m</error>(Foo<Outer<Integer>.Inner> foo) {
        <error descr="Incompatible types. Found: 'Foo<Outer<java.lang.Integer>.Inner>', required: 'Foo<Outer.Inner>'">return foo;</error>
    }
}
class Foo<X> {}