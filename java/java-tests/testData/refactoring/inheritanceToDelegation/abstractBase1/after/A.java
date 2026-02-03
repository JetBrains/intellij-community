public class A {
    private final MyBase myDelegate = new MyBase();

    private class MyBase extends Base {
        public void run() {
             System.out.println("From A");
        }
    }
}