// "Make 'A' extend 'B'" "true"
class B {}

class A extends B {
    A(B b) {
    }

    A a = new A(this);
}
