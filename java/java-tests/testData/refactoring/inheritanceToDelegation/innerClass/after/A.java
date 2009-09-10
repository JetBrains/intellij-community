public class A {
    public final MyBase myDelegate;

    public A() {
        myDelegate = new MyBase(27);
    }

    private class MyBase extends Base {
        public MyBase(int i) {
            super(i);
        }

        public void methodToOverride() {
            System.out.println("Hello from B");
        }
    }
}