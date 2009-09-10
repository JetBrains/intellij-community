public class A {
    private void foo() {
        Runnable a = new Runnable() {
            private int a;

            public void run() {
                newMethod();
            }

            private void newMethod() {
                a = 2;
            }
        };
    }
}