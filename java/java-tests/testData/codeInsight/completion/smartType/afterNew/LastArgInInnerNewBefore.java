class B {
    class A {
      A(int i) {}
    }
    static B createB(A a) {}
    public static void main(String[] args) {
        int i;
        foo(createB(new A(<caret>)));
    }
}