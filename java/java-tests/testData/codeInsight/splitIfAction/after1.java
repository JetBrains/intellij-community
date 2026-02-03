class C {
    void foo() {
        if (<caret>a) {
            if (b) {
                call();
            }
            else {
                dontCall();
            }
        }
        else {
            dontCall();
        }
    }
}