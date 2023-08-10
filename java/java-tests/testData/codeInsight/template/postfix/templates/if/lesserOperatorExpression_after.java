package templates;

public class Foo {
    void m(int Bar, int Integer) {
        if (Bar<Integer) {
            <caret>
        }
        return;
    }
}

class Bar<T> {

}
