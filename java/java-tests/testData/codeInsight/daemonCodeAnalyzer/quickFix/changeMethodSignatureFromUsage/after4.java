// "<html> Change signature of f(int, <b>char</b>, <b>int</b>, String, <b>int</b>, <b>Object</b>)</html>" "true"
class A {
    void f(int i, char c, int i1, String s, int i2, Object o) {}
    public void foo() {
        <caret>f(1,'2',4,"",1,null);
    }
}