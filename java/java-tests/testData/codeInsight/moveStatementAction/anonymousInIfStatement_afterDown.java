class C {
    void m() {
        if (true) {
            new Runnable() {};
        }
        if (true) {
            int a = 1;
            new Runnable() {};
        }
    }
}