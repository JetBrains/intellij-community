// "Change signature of 'A()' to 'A(int, int, String)'" "true"
class A {
    A() {
        new A<caret>(1,1,"4");
    }
}