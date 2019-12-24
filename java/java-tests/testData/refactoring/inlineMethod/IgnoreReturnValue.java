class Foo {
  void method() {
    new Bar().<caret>nonVoidMethod();
  }
}
class Bar {
  Bar nonVoidMethod() {
    return this.innerMethod();
  }
  Bar innerMethod() {
    return this;
  }
}