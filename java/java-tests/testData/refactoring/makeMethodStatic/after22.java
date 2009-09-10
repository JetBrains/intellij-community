class Test {

    public static void foo(Test anObject) {
        anObject.bar();
    }

    void bar() {
    }

    class A {
        void blah() {
            foo(Test.this);
        }
    }
}