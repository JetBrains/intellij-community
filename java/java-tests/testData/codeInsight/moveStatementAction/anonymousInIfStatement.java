class C {
    void m() {
        if (true) {
            new Runnable() {};
        }
        int a = 1;<caret>
        if (true) {
            new Runnable() {};
        }
    }
}