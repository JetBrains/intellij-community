// "Change 2nd parameter of method 'f' from 'String' to 'int'" "true"
class A {
    void f(int i, String s, int i2) {}
    public void foo() {
        <caret>f(1,1,'4');
    }
}