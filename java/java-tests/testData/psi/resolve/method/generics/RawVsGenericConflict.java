class TestClassT {
    static {
        B.<ref>foo(TestClassT.class);
    }

    public static class A {
        public static A foo(Class<?> type) {
            return new A();
        }
    }
    public static class B extends A {
        public static B foo(Class type) {
            return new B();
        }
    }
}