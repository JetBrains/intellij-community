public class Foo {
    void f(byte x) {
        String s;
        s = switch (x) {
            <caret>
        }
    }
}