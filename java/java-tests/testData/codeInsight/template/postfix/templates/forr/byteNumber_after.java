public class Foo {
    void m() {
        byte foo = 100;
        for (byte b = foo; b > 0; b--) {
            <caret>
        }
    }
}