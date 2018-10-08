// "Join declaration and assignment" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo() {
        /*comment 1*/
        bar();/*comment 2*//*comment 3*///comment 4
        // comment A
        /*comment B*/
        int n = 1;// comment C
    }
    void bar(){}
}