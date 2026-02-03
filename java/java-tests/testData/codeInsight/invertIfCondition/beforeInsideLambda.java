// "Invert 'if' condition" "true"
class A {
    public void foo() {
        Runnable r = () -> {
            if (System.currentTimeMillis() <caret>> 1) {
                return;
            }
            System.err.println("Elvis lives");
        };
    }
}