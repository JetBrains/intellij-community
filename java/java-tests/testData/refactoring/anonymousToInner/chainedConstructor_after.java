public class Test {
    void test() {
        String hello = "Hello World";

        new Inner(hello).print();
        new Inner(null, hello).print();
    }

    private static class Inner {
        private final String hello;

        public Inner(String hello) {
            this(null, hello);
        }

        public Inner(Object x, String hello) {
            this.hello = hello;
        }

        void print() {
            System.out.println(hello);
        }
    }
}
