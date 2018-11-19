// "Join declaration and assignment" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo() {
        int n;
        bar();
        <caret>n = 1;
    }
    void bar(){}
}