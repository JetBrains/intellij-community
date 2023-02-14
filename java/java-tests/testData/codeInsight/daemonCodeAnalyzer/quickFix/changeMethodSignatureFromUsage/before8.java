// "<html> Change signature of A(<b>int</b>, <b>int</b>, <b>String</b>)</html>" "true-preview"
class A {
    A() {
        new A<caret>(1,1,"4");
    }
}