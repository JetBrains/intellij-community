class C {
    void m() {
        new Runnable() {
            <caret>public void run() {}
        };
    }
}