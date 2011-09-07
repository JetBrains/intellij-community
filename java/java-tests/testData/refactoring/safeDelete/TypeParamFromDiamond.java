public class Test<<caret>T, U> {}

class Foo {
        void test() {
                Test<String, Long> test = new Test<>();
                Test<String, Long> test2 = new Test<String, Long>();
        }
}