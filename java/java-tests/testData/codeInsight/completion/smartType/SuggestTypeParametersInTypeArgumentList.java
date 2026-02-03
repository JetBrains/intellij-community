class StringEx<T extends String> {
}
class Outer<T extends String> {
  static class CompletionTest<T extends String> {
    private StringEx<<caret>> myString;
  }
}

