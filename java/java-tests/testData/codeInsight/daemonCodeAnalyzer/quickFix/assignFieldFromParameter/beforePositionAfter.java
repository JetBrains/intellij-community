// "Assign parameter to field 'myId'" "true-preview"

class Person {
    int a;
    int myId;
    void f(int a, int id<caret>) {
        this.a = foo(a);
    }
    int foo(int a) {return a;}
}