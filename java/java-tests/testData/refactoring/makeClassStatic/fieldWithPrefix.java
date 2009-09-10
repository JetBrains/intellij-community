class T {
    Object myObject;

    void test() {
        new MyRunnable().run();
    }

    private class <caret>MyRunnable implements Runnable {
        public void run() {
            myObject.toString();
        }
    }
}
