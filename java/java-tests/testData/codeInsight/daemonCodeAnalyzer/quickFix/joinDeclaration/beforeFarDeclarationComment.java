// "Join declaration and assignment" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo() {
        /*comment 1*/int /*comment 2*/ n /*comment 3*/; //comment 4
        bar();
        // comment A
        /*comment B*/
        <caret>n = 1; // comment C
    }
    void bar(){}
}