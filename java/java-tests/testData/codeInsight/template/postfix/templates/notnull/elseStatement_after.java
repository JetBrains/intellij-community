public class Foo {
    void m(Object o) {
        if (true) {

        }
        else if (o != null) {
            <caret>
        }
    }
}