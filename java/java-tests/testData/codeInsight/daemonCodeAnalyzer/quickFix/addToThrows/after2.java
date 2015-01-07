// "Add exception to method signature" "true"
class C {
    static class E1 extends Exception { }
    static class E2 extends Exception { }

    static class MyResource implements AutoCloseable {
        public void doSomething() throws E1 { }
        public void close() throws E2 { }
    }

    void m() throws E1, E2 {
        try (MyResource r = new MyResource()) {
        }
    }
}