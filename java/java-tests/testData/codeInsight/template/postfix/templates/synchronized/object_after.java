public class Foo {
    void m(Object o) {
        synchronized (o) {
            <caret>
        }
    }
}