// "Add exception to method signature" "true-preview"
class C {
    static class E1 extends Exception { }

    static class MyResource implements AutoCloseable {
        public void close() throws E1 { }
    }

    void m() throws E1 {
        try (MyResource r = new MyResource()) {
        }
    }
}