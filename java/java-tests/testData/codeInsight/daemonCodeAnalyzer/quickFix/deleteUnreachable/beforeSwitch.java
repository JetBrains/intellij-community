// "Delete unreachable statement" "true-preview"
class Never {
  void foo(int n) {
    return;
    <caret>switch (n) {
      case 1:
        System.out.println("Never");
    }
  }
}