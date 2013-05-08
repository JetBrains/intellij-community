class C {
    void f() {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                <caret>
            }
        };
    }
}