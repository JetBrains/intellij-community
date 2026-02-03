class A {
    int <caret>myField;
}

class B {
    int method(A a) {
        return A.myField;
    }
}