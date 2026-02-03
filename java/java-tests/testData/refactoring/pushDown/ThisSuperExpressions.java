class A {
  class In<caret>ner {
    void m() {
      A.super.toString();
      A a = A.this;
    }
  }
}

class B extends A {}