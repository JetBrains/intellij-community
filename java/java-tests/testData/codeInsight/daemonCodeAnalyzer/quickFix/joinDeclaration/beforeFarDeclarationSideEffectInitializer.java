// "Join declaration and assignment" "false"
class C {
    void foo() throws Exception {
        int n = System.in.read();
        bar();
        <caret>n = 1;
    }
    void bar(){}
}