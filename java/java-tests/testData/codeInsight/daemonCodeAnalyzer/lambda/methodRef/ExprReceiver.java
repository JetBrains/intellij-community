class ThreadExample {
    interface Function<T, R> {

        R apply(T t);
    }
    {
        A a = new A();
        Function<? super A,? extends String> foo = a::<error descr="Cannot resolve method 'foo'">foo</error>;
    }

    static class A {
        public String foo() { return "a"; }
    }
}

