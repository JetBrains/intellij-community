// "Create constructor matching super" "false"
class A {
    public A(int i) {
    }
}
class B extends A {
    public B<caret>(int i) {
    }
}