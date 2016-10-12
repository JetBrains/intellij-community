class A {
  final int field;
  A(int field) {
    this.field = field;
  }

  A(A delegate, int x) {
    this(<caret>)
  }
}