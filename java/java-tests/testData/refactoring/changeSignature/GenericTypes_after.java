class C<T> {
    T method(T x, C<T> y);
}

class C1 extends C<String> {
    String method(String x, C<String> y);
}