public class Test<T<caret>T> {}

class Foo {
        void test() {
                Test<String> test = new Test<>();
                Test<String> test2 = new Test<String>();
        }
}