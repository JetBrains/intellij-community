public class Test {
    void test(boolean condition) {
        <selection>foo("one");</selection>
        foo("one");
        foo("two");
    }

    void foo(String name) {
    }
}