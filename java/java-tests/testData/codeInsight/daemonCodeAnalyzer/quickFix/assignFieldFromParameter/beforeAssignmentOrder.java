// "Assign parameter to field 'myName'" "true-preview"

class Person {
    int myId;
    String myName;
    void f(int id, String name<caret>) {
        myId = id;
    }
}