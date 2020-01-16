// "Make 'I.foo' public" "true"
interface I {
  private void foo() { }
}

class A implements I {
  {
    this.fo<caret>o();
  }
}