// "Replace 'E1' with more generic 'E'" "true-preview"
class C {
    static class E extends Exception { }
    static class E1 extends E { }

    static class MyResource implements AutoCloseable {
        public MyResource() throws E1 { }
        public void close() throws E { }
    }

    void f() {
        try (<caret>MyResource r = new MyResource()) {
        } catch (E1 e) {
            e.printStackTrace();  
        }
    }
}