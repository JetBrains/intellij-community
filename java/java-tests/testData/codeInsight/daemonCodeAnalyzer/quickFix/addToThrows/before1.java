// "Add exception to method signature" "true"
class C {
    static class E1 extends Exception { }

    static class MyResource implements AutoCloseable {
        public void close() throws E1 { }
    }

    void m() {
        try (<caret>MyResource r = new MyResource()) {
        }
    }
}