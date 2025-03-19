class C {
    void m() throws Exception {
        try (AutoCloseable r1 = null) {
            System.out.println(r1 + ", " + r1);
        }
    }
}