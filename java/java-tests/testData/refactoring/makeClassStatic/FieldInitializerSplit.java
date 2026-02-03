class Outer {
  class In<caret>ner {
    Object x = Outer.this.getClass();

  }
}