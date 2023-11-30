class C {
    void m() throws Exception {
        try (AutoCloseable r1 = null; AutoCloseable <caret>r2 = r1; AutoCloseable r3 = null) {
            System.out.println(r1 + ", " + r2 + ", " + r3);
        }
    }
}