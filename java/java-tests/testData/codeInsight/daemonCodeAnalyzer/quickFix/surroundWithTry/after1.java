// "Surround with try/catch" "true"
class C {
    static class E1 extends Exception { }

    static class MyResource implements AutoCloseable {
        public void close() throws E1 { }
    }

    void m() {
        try {
            try (MyResource r = new MyResource()) {
            }
        } catch (E1 e1) {
            <selection>e1.printStackTrace();</selection>
        }
    }
}