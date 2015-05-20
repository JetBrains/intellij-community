// "Create method 'f'" "true"
class A {
    {
         f<caret>(A::foo);
    }
    static int foo() {
      return 42;
    }
}
