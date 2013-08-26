class ThreadExample {
    interface Function<T, R> {

        R apply(T t);
    }
    {
        A a = new A();
        <error descr="Incompatible types. Found: '<method reference>', required: 'ThreadExample.Function<? super ThreadExample.A,? extends java.lang.String>'">Function<? super A,? extends String> foo = a::foo;</error>
    }

    static class A {
        public String foo() { return "a"; }
    }
}

