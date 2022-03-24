// "Remove unreachable branches" "true"
class Test {
  final String s = "abc";

  int test() {
    return switch (s) {
      case <caret>String ss && ss.length() <= 3 -> 1;
      case "fsd" -> 2;
      case default -> 3;
    };
  }
}