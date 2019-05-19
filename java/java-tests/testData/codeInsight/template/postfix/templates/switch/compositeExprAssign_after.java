public class Foo {
    void m() {
        int i;
        i = switch (42 + 42) {
            <caret>
        }
    }
}