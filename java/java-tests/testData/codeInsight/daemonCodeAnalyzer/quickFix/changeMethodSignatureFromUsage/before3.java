// "Change signature of 'f(int, String)' to 'f(int, char, String)'" "true"
class A {
    void f(int i,String s) {}
    public void foo() {
        <caret>f(1,'2',"");
    }
}