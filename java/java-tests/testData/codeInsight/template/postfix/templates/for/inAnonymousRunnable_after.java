class Example {
    <T> void test(T[] foo) {
        new Runnable() {
            @Override
            public void run() {
                for (T t : foo) {

                }
            }
        };
    }
}