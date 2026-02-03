class A {
    void foo(final MyObject obj) {
        final MyObject _obj;
        _obj = obj;
        new Runnable() {
            public void run() {
                System.out.println(<caret>_obj);
            }
        }.run();
    }
}