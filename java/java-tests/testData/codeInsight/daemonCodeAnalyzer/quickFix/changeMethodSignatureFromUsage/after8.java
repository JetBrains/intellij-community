// "Change signature of 'A()' to 'A(int, int, String)'" "true"
class A {
    A(int i, int i1, String s) {
        new A<caret>(1,1,"4");
    }
}