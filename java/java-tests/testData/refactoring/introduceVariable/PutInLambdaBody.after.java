class Foo {
    interface I<T> {
        T m(Object op);
    }

    interface J<T> {
        int m(T o);
    }


    void f(J r) {}
    void f(I<String> r) {}

    {
        f((Object a) -> {
            int c = 1;
            return c;
        });
    }
}