class Test {
  private final String test = "";

  {
    foo();
  }
  
  void fo<caret>o() {
    if (test.isEmpty());
  }
}