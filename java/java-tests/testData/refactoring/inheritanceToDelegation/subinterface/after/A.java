public class A extends B {
    private final MyJ myDelegate = new MyJ();

    public J getMyDelegate() {
        return myDelegate;
    }

    private class MyJ implements J {
        public void run() {
        }
    }
}