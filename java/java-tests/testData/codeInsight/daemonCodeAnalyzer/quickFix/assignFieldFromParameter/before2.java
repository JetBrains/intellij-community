// "Assign parameter to field 'myId'" "false"

class Person {
    int myId;
    void f(int id<caret>) {
        myId = id;
    }
}