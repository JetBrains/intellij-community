// "Remove local variable 'problematic'" "true-preview"
class C {
  native int foo();

  void case01() {
      if (1 > 2) System.out.println(foo() + foo());
  }
}
