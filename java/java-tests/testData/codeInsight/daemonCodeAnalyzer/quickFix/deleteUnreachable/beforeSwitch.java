// "Delete unreachable statement" "true"
class Never {
  void foo(int n) {
    return;
    <caret>switch (n) {
      case 1:
        System.out.println("Never");
    }
  }
}