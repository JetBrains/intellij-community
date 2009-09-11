class A {
    int myNewField;
}

class B {
    int method(A a) {
        return A.myNewField;
    }
}