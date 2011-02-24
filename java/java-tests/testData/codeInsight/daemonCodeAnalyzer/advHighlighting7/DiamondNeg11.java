class Neg11 {

    void test() {
        class Foo<X extends Number> { }
        Foo<?> f1 = new <error descr="Cannot resolve symbol 'UndeclaredName'">UndeclaredName</error><>(); //this is deliberate: aim is to test erroneous path
        Foo<?> f2 = new <error descr="Cannot resolve symbol 'UndeclaredName'">UndeclaredName</error><>() {}; //this is deliberate: aim is to test erroneous path
    }
}
