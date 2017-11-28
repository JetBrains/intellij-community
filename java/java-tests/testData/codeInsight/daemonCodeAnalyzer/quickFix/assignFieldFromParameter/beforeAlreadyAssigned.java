// "Assign Parameter to Field 'myA'" "false"

class Person {
    int myA;
    int myId;
    void f(int <caret>a, String id) {
        this.myA = foo(a);
    }
    int foo(int a) {return a;}
}