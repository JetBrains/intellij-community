class C {
    void foo() {
        if (a || b &<caret>& c) {
            call();
        }
    }
}