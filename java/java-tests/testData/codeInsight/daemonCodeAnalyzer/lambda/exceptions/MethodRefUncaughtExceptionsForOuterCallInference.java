class Test {
    interface I<E extends Throwable> {
        void foo() throws E;
    }
    static class Ex extends Exception {}

    <E extends Throwable> void bar(I<E> s) throws E {
        s.foo();
    }

    void baz(I<Ex> s) throws Ex {
        bar(s::foo);
    }
}