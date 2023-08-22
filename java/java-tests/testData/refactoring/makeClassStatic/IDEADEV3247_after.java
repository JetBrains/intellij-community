class Test8 {

    void bar() {
    }

    static class B {

        private final Test8 anObject;
        int c;

        public B(Test8 anObject) {
            this.anObject = anObject;
        }

        void foo() {
            c = 10;
            anObject.bar();
        }
    }
}
