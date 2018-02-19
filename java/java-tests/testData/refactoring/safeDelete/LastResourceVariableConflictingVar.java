class ARM {
    void f() {
        System.out.println("before");
        try (AutoCloseable <caret>r = null) {
            int i = 0;
            System.out.println("inside");
        }
        int i = 0;
    }
}