package templates.

public class Foo {
    void m(boolean x) {
        x.else<caret>
        value = dummyAssignment;
    }
}