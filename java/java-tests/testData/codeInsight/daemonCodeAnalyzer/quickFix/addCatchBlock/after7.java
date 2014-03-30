// "Add Catch Clause(s)" "true"
class Test {
    static class E1 extends Exception { }
    static class E2 extends Exception { }

    static class MyResource implements AutoCloseable {
        public MyResource() throws E1 { }
        public void close() throws E2 { }
    }

    void m() {
        try (MyResource r = new MyResource()) {
        } catch (E2 e2) {
            <selection>e2.printStackTrace();</selection>
        } catch (E1 e1) {
            e1.printStackTrace();
        }
    }
}