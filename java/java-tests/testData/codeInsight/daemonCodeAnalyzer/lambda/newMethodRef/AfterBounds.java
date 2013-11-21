import java.util.List;
class Test {

    interface I<T> {
        T foo();
    }

    static class Foo<X> {
        static <T> Foo<T> foo() { return null; }
    }

    <T, S extends Foo<T>> List<T> meth(I<S> p) { return null; }

    void test() {
        List<?> l1 = meth(Foo::new);
        List<?> l2 = meth(Foo::foo);
        List<String> l3 = meth(Foo::new);
        List<String> l4 = meth(Foo::foo);
    }
}