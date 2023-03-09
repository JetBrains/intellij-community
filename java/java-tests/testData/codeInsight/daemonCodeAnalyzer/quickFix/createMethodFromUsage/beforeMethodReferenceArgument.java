// "Create method 'f'" "true-preview"
class A {
    {
         f<caret>(A::foo);
    }
    static int foo() {
      return 42;
    }
}
