class Test {
    interface A<T> {
        void _(T... t);
    }

    static void foo(final A<?> bar) {
        bar._<error descr="Cannot resolve method '_(String)'">("")</error>;
    }
    static void foo1(final A<? extends String> bar) {
        bar._<error descr="Cannot resolve method '_(String)'">("")</error>;
    }

    static void foo2(final A<? extends Integer> bar) {
        bar._<error descr="Cannot resolve method '_(String)'">("")</error>;
    }


    public static void main(String[] args) {
        foo(new A<Integer>() {
            public void _(final Integer... t) {

            }
        });
    }
}
