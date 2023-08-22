// "Remove local variable 'problematic'" "true-preview"
class C {
  native int foo();

  void case01() {
    int <caret>problematic;
    if (1 > 2) System.out.println((problematic = foo()) + (problematic = foo()));
  }
}
