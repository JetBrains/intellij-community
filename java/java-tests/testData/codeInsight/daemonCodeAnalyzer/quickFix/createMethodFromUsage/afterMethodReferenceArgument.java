// "Create method 'f'" "true"
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
