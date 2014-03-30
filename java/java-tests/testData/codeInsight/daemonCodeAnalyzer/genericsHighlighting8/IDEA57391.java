abstract class B<T> {
    abstract <S extends T> void foo(T x, S y);
}

class A extends B<String> {
    void foo(String x, String y) {}
}
