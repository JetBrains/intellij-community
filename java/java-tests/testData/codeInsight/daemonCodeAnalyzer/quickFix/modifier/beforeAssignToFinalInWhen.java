// "Make 'i' not final" "false"
class Main {
  void foo(Object obj) {
    final int i = 41;
    switch (obj) {
      case String s when s.length() == ++i<caret> -> {}
      default -> {}
    }
  }
}