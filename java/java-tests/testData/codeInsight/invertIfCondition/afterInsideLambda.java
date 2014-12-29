// "Invert 'if' condition" "true"
class A {
    public void foo() {
        Runnable r = () -> {
            if (System.currentTimeMillis() <= 1) {
                System.err.println("Elvis lives");
            }
            else {
                return;
            }
        };
    }
}