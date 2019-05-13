// "Join declaration and assignment" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo() {
        /*comment 1*/
        bar();
        // comment A
        /*comment B*/
        //comment 4
        int /*comment 2*/ n /*comment 3*/ = 1; // comment C
    }
    void bar(){}
}