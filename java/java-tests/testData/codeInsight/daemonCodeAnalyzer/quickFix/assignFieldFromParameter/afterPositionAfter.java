// "Assign parameter to field 'myId'" "true-preview"

class Person {
    int a;
    int myId;
    void f(int a, int id) {
        this.a = foo(a);
        myId = id;
    }
    int foo(int a) {return a;}
}