// "Make 'A' extend 'B'" "true"
class B {}

class A {
    A(B b) {
    }

    A a = new A(th<caret>is);
}
