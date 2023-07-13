// "Remove unreachable branches" "true-preview"

class Test {
  final String s = "abc";

  void test() {
    switch (s) {
      case <caret>String ss when ss.length() <= 3:
        System.out.println(1);
        break;
      case "fsd":
        System.out.println(2);
        break;
      default:
        System.out.println(3);
        break;
    }
  }
}