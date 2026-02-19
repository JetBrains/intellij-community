class T {
    Object myObject;

    void test() {
        new MyRunnable(myObject).run();
    }

    private static class <caret>MyRunnable implements Runnable {
        private final Object myObject;

        public MyRunnable(Object object) {
            this.myObject = object;
        }

        public void run() {
            myObject.toString();
        }
    }
}
