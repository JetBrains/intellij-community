// "Delete unreachable statement" "true-preview"
class Never {
  void foo(boolean b) {
    return;
    <caret>do {
      System.out.println("Never");
    }
    while (b);
  }
}