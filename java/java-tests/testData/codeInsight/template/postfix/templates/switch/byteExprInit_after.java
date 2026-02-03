public class Foo {
    void f(byte x) {
        String s = switch (x) {
            <caret>
        }
    }
}