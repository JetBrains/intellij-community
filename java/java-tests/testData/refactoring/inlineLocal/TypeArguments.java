class Example {
    private class X<T> {}
    private <T> X<T> foo() { return new X<T>(); }
    private <T> X<T> boo(X<T> x) {return x;}
    private void goo() {
        X<Integer> f = foo();
        X<Integer> x = boo(<caret>f);
    }
}