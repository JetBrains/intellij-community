class PP {
    void foo(){}
}

class PPImpl extends PP {
    void foo() {
        foo();
        bazz(0);
    }

    private void <caret>bazz(int k) {
        bazz(k);
        bazz(k);
    }
}