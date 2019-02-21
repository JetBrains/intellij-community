public class Foo {
    void m() {
        int i = switch (42 + 42) {
            <caret>
        }
    }
}