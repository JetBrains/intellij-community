class Test {
    interface A<T> {
        void _(T... t);
    }

    static void foo(final A<?> bar) {
        bar._("");
    }
    static void foo1(final A<? extends String> bar) {
        bar._("");
    }

    static void foo2(final A<? extends Integer> bar) {
        bar._<error descr="'_(capture<? extends java.lang.Integer>...)' in 'Test.A' cannot be applied to '(java.lang.String)'">("")</error>;
    }


    public static void main(String[] args) {
        foo(new A<Integer>() {
            public void _(final Integer... t) {

            }
        });
    }
}
