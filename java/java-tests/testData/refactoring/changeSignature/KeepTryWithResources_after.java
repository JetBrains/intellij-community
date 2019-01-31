class Scratch {

    static class C implements AutoCloseable {
        public void close() {}
    }

    public static void main(String[] args) throws Exception {
        try (C c = new C()) {
            foo();
        }
    }

    static void foo() { }
}