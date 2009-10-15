public class Aaaaaaa {

    Object foo() {}

    void bar() {
        if (foo() instanceof String) {
            ((String) foo()).substring(<caret>)
        }
    }

}
