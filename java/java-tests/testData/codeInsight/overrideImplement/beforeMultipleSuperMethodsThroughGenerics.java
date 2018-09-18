interface I<T> {
    String foo(T x);
    Object foo(String x);
}

class C implements I<String> {
    <caret>
}