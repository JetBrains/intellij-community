// "Replace method call on lambda with lambda body" "false"

class Test {
    void foo() {
        ((Runnable) () -> {
            if (true) return;
            System.out.println("");
        }).r<caret>un();
        System.out.println("");
    }
}