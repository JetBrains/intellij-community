class Test {
    interface I<T extends Number> {
        void _(T t);
    }
    
    void foo(Object o) { }

    void test() {
        I<?> i1 = (x) -> {};
        I<?> i2 = this :: foo;
    }
}
