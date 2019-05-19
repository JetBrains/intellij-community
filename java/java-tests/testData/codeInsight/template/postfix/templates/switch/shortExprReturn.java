public class Foo {
    String f(short x) {
        return 1 + x.switch<caret>
    }
}