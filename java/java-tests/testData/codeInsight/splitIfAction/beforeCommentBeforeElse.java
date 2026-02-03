class C {
    void foo() {
        if (a &<caret>& b) {
            call();
        } // foo
        else if (a && c) {
            otherCall();
        } // bar
    }
}