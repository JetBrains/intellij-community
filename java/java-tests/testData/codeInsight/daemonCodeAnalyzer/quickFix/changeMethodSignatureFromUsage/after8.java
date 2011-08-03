// "<html> Change signature of A(<b>int</b>, <b>int</b>, <b>String</b>)</html>" "true"
class A {
    A(int i, int i1, String s) {
        new A<caret>(1,1,"4");
    }
}