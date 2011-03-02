// "Generalize catch for 'C.E1' to 'C.E'" "true"
class C {
    static class E extends Exception { }
    static class E1 extends E { }

    static class MyResource implements AutoCloseable {
        public MyResource() throws E1 { }
        public void close() throws E { }
    }

    void f() {
        try (MyResource r = new MyResource()) {
        } catch (E e) {
            e.printStackTrace();  
        }
    }
}