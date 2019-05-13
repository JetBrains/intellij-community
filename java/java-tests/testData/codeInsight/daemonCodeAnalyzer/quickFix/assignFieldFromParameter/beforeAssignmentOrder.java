// "Assign parameter to field 'myName'" "true"

class Person {
    int myId;
    String myName;
    void f(int id, String name<caret>) {
        myId = id;
    }
}