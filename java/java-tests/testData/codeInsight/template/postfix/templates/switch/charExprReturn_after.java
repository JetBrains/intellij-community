public class Foo {
    String f(char x) {
        return switch (x) {
            <caret>
        }
    }
}