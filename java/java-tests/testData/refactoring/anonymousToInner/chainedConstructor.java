public class Test {
    void test() {
        String hello = "Hello World";
        class <caret>Inner {
            public Inner() {
                this(null);
            }

            public Inner(Object x) {
            }

            void print() {
                System.out.println(hello);
            }
        }

        new Inner().print();
        new Inner(null).print();
    }
}
