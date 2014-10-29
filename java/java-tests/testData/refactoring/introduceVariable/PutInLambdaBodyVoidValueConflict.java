class Foo {
    interface I<T> {
        T m(Object op);
    }

    interface J<T> {
        void m(T o);
    }


    void f(J r) {}
    void f(I<String> r) {}

    {
        f((a) -> <selection>1</selection>);
    }
}