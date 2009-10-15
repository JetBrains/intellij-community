class Generic<T> { }

class Other {
  static <T> T take(Generic<T> thing) { return null; }
}

class Tester {
  Generic<String> str;
  void method() {
    CharSequence x = Other.take(str)<caret>;
  }
}