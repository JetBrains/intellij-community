public class Foo {
    void f() {
        String s = switch ("abc") {
            <caret>
        }
    }
}