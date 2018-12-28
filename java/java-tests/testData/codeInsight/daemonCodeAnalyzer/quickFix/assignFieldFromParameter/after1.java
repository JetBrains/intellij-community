// "Assign parameter to field 'myId'" "true"

class Person {
    int myId;
    void f(int id) {
        myId = id;<caret>
    }
}