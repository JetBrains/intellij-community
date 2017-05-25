// "Replace method call on lambda with lambda body" "false"

class Test {
    void foo() {
        Runnable r = () -> {
            if (true) return;
            System.out.println("");
        };
        r.r<caret>un();
        System.out.println("");
    }
}