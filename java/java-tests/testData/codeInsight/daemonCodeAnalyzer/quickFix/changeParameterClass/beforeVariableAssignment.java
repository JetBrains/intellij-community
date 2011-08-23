// "Make 'A' extend 'B'" "true"
class B {}

class A {
    int foo(B b) {
        B bz = t<caret>his;
        return 1;
    }
}
