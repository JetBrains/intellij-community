// "Assign Parameter to Field 'myId'" "true"

class Person {
    int a;
    int myId;
    void f(int a, int id<caret>) {
        this.a = foo(a);
    }
    int foo(int a) {return a;}
}