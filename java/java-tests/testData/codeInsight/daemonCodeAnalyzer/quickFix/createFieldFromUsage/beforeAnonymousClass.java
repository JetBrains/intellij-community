// "Create field 'field'" "true-preview"
class Main {
    static void foo() {
        new Object() {
            void bar() {
                this.<caret>field = 0;
            }
        };
    }
}
