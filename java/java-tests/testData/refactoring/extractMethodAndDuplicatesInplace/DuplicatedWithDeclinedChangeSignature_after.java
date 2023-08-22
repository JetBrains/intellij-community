public class Test {
    void test(boolean condition) {
        extracted();
        extracted();
        foo("two");
    }

    private void extracted() {
        foo("one");
    }

    void foo(String name) {
    }
}