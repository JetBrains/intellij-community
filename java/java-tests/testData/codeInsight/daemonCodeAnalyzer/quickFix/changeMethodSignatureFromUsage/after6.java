// "Change 2nd parameter of method 'f' from 'String' to 'int'" "true-preview"
class A {
    void f(int i, int s, int i2) {}
    public void foo() {
        <caret>f(1,1,'4');
    }
}