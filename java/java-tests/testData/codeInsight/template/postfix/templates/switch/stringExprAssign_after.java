public class Foo {
    void f() {
        String s;
        s += switch ("abc") {
            <caret>
        }
    }
}