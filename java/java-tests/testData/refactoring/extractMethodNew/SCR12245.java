public class A {
    private void foo() {
        Runnable a = new Runnable() {
            private int a;

            public void run() {
                <selection>a = 2</selection>;
            }
        };
    }
}