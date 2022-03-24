// "Replace with 'A.this'" "true"
class A {
  class B {
    B(A B.this<caret>) {}
  }
}