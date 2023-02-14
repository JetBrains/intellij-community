// "Create method 'f'" "true-preview"
class A {
    {
         f(A::foo);
    }

    private void f(Object foo) {
        <selection></selection>
    }

    static int foo() {
      return 42;
    }
}
