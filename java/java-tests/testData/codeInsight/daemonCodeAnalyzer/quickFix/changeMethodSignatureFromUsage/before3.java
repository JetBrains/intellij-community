// "Add 'char' as 2nd parameter to method 'f'" "true-preview"
class A {
    void f(int i,String s) {}
    public void foo() {
        <caret>f(1,'2',"");
    }
}