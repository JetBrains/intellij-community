// "Assign Parameter to Field 'myName'" "true"

class Person {
    int myId;
    String myName;
    void f(int id, String name) {
        myId = id;
        myName = name;<caret>
    }
}