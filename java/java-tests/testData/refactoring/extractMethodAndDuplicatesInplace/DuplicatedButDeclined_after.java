public class Test {
    void test(boolean condition) {
        extracted();
        foo("one");
        foo("two");
    }

    private void extracted() {
        foo("one");
    }

    void foo(String name) {
    }
}