// "Remove unreachable branches" "true"

class Test {
  final String s = "abc";

  void test() {
    switch (s) {
      case <caret>String ss && ss.length() <= 3:
        System.out.println(1);
        break;
      case "fsd":
        System.out.println(2);
        break;
      case default:
        System.out.println(3);
        break;
    }
  }
}