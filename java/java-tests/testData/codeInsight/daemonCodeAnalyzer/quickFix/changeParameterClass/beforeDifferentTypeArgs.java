// "Make 'Generic' extend 'Generic'" "false"
class Generic<E> {
    Generic(E arg) { }
}

class Tester {
    void method() {
        Generic<Integer> aIntegerGeneric = new <caret>Generic<String>("");
    }
}
