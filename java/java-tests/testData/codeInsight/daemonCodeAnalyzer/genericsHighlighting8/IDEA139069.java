abstract class A<K extends A<?>> {
    void bar(K x) {
        String s = foo(x).toLowerCase();
    }

    abstract <T extends A<S>, S extends A<?>> void foo(A<T> x);
    abstract String foo(Object x);
}