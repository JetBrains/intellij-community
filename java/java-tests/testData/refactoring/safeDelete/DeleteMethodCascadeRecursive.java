class Test {
    void f<caret>oo() {
        bar();
        if (false) {
            foo();
        }
    }

    void bar(){
        baz();
        if (false) {
            bar();
        }
    }

    void baz(){}
}