// "Delete unreachable statement" "true"
class Never {
  void foo(boolean b) {
    return;
    <caret>do {
      System.out.println("Never");
    }
    while (b);
  }
}