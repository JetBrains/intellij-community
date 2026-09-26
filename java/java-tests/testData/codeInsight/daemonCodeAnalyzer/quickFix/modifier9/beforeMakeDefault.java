// "Make 'I.foo()' public" "true-preview"
interface I {
  private void foo() { }
}

class A implements I {
  {
    this.fo<caret>o();
  }
}