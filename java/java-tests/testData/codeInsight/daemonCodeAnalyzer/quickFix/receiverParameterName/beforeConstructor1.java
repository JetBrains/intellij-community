// "Replace with 'A.this'" "true-preview"
class A {
  class B {
    B(A this<caret>) {}
  }
}