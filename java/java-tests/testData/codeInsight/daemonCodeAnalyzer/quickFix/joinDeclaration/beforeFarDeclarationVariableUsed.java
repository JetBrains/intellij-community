// "Join declaration and assignment" "false"
class C {
    void foo() {
        int n = 0;
        bar(n);
        <caret>n = 1;
    }
    void bar(int i){}
}