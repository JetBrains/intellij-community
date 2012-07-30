class WildcardBounds {

    interface I<T> {
        T foo(T a, T b);
    }

    void m1(I<? extends String> f1) {}
    void m2(I<? super String> f2) {}
    void m3(I<?> f3) {}

    I<? extends String> f1 = (a, b) -> a;
    I<? super String> f2 = (a, b) -> a;
    I<?> f3 = (a, b) -> a;

    {
        m1((a, b) -> a);
        m2((a, b) -> a);
        m3((a, b) -> a);
    }
}
