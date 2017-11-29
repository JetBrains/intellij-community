// "Create field 'field'" "true"
class Main {
    static void foo() {
        new Object() {
            private int field<caret>;

            void bar() {
                this.field = 0;
            }
        };
    }
}
