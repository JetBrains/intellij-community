public class Foo {
    String f(short x) {
        return switch (1 + x) {
            <caret>
        }
    }
}