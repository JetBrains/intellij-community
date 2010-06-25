// "Create Field 'field'" "true"
class Main {
    static void foo() {
        new Object() {
            void bar() {
                this.<caret>field = 0;
            }
        };
    }
}
