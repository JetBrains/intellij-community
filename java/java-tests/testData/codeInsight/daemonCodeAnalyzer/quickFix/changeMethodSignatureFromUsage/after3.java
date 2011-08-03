// "Add char as 2 parameter to method f" "true"
class A {
    void f(int i, char c, String s) {}
    public void foo() {
        <caret>f(1,'2',"");
    }
}