class Test{
  void foo() {
  Label:
    for ( ; ; ) {
      <caret>break;
    }
  }
}
