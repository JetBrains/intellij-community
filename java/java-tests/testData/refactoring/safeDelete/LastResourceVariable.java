class ARM {
    void f() {
        System.out.println("before");
        try (AutoCloseable <caret>r = null) {
            System.out.println("inside");
        }
    }
}