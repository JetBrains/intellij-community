public class Test<U> {}

class Foo {
        void test() {
                Test<Long> test = new Test<>();
                Test<Long> test2 = new Test<Long>();
        }
}