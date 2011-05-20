public class Foo {
    void test(int i) {
        while (i-- > 1) {
            i = 1;
        }
        <caret>
    }
}