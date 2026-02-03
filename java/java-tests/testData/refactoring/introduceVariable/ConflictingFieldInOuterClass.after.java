public class Test4 {
    String text = "Hello world!";
    private class Q {
        void foo() {
            final String text = Test4.this.text;
            System.out.println("text = " + text);
        }
    }
}