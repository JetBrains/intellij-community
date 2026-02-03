class A {
    public void f() {
        Inner i = new Inner();
        i.q = 2;
    }

    private class <caret>Inner implements Runnable {
        public int q = 1;

        public void run() {
        }
    }
}