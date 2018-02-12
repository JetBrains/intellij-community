// "Delete unreachable statement" "true"
class Never {
  void foo(boolean b) {
    return;
    <caret>if (b) {
      System.out.println("Never");
    } else {
      System.out.println("Never ever");
    }
  }
}