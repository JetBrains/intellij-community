class TestClassT {
    static {
        new B().<ref>foo(TestClassT.class);
    }

    public static class A {
        public A foo(Class<?> type) {
            return new A();
        }
    }
    public static class B extends A {
      @Override
        public B foo(Class type) {
            return new B();
        }
    }
}