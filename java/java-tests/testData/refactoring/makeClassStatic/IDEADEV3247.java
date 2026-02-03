class Test8 {

    void bar() {
    }

    class <caret>B {

        int c;

        void foo() {
            c = 10;
            bar();
        }
    }
}
