// "Delete unreachable statement" "true-preview"
class Never {
  void foo() {
    return;
    <caret>for (int i = 0; i < 2; i++) {
      System.out.println("Never");
    }
  }
}