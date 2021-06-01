class Outer {
  void bar() {}
  class In<caret>ner {
    {
      bar();
    }

  }
}