// "Delete unreachable statement" "true"
class Never {
  void foo(int n) {
    return;
    <caret>System.out.println(n);
  }
}