// "Remove 'new'" "false"

class A {
  class B {}
  B B() {
    return new A.<caret>B();
  }
}
