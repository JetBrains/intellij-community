// "Assign Parameter to Field 'myId'" "false"

class Person {
    int myId;
    void f(int id<caret>) {
        myId = id;
    }
}