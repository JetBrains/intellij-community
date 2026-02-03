public abstract class A {
    public final MyBase myDelegate = new MyBase();

    protected abstract void run();

    private class MyBase extends Base {
        public void run() {
            A.this.run();
        }
    }
}