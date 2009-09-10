class Test8 {

    void bar() {
    }

    static class B {

        int c;
        private Test8 anObject;

        public B(Test8 anObject) {
            this.anObject = anObject;
        }

        void foo() {
            c = 10;
            anObject.bar();
        }
    }
}
