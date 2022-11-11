// "Delete unreachable statement" "true-preview"
class Never {
  void foo(int n) {
    return;
    <caret>System.out.println(n);
  }
}