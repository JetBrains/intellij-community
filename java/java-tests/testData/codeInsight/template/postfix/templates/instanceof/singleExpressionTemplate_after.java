public class Foo {
    void m(Object o) {
        o instanceof Integer ? ((Integer) o)<caret> : null;
    }
}