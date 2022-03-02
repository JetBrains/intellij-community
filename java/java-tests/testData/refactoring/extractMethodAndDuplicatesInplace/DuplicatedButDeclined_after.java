public class Test {
    void test(boolean condition) {
        extracted("one");
        foo("one");
        foo("two");
    }

    private void extracted(String one) {
        foo(one);
    }

    void foo(String name) {
    }
}