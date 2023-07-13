// "Unwrap 'switch'" "true-preview"
class Test {
  void foo(Object obj) {
    int answer = sw<caret>itch (obj) {
      default -> 42;
    };
  }
}