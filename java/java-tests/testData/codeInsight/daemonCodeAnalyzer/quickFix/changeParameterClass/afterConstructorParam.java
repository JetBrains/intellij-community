// "Make 'A' extend 'B'" "true-preview"
class B {}

class A extends B {
    A(B b) {
    }

    A a = new A(this);
}
