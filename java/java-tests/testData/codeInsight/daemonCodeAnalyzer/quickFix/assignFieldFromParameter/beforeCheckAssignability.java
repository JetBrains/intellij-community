// "Assign parameter to field 'myId'" "false"

class Person {
    int a;
    int myId;
    void f(int a, String id<caret>) {
        this.a = foo(a);
    }
    int foo(int a) {return a;}
}