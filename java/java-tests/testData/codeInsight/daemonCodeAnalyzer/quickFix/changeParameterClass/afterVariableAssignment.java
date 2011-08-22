// "Make 'A' extend 'B'" "true"
class B {}

class A extends B {
    int foo(B b) {
        B bz = this;
        return 1;
    }
}
