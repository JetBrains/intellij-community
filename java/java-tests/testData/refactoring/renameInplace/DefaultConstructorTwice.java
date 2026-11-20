class Outer {
  static class SomeClass {}

  static void main() {
    SomeClass someClass = new <caret>SomeClass();
  }
}
