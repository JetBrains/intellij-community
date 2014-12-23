class Example {
    <T> void test(T[] foo) {
        new Runnable() {
            @Override
            public void run() {
                foo.for<caret>
            }
        };
    }
}