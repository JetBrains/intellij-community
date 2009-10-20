public class Aaaaaaa {

    Object foo() {}

    void bar() {
        if (foo() instanceof String) {
            foo().substr<caret>
        }
    }

}
