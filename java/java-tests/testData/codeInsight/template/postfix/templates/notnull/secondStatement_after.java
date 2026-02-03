public class Foo {
    void m(Object o) {
        if (o != null) {
            <caret>
        }
        value = 123;
    }
}