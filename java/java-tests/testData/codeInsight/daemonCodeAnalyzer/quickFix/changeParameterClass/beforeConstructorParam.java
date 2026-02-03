// "Make 'A' extend 'B'" "true-preview"
class B {}

class A {
    A(B b) {
    }

    A a = new A(th<caret>is);
}
