// "Create Field 'field'" "true"
class Main {
    static void foo() {
        new Object() {
            public int field<caret>;

            void bar() {
                this.field = 0;
            }
        };
    }
}
