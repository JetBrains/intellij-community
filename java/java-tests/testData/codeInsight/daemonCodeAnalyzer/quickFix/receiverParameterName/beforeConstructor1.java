// "Replace with 'A.this'" "true"
class A {
  class B {
    B(A this<caret>) {}
  }
}