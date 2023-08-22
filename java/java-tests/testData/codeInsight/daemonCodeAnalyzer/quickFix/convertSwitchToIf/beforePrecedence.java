// "Replace 'switch' with 'if'" "true-preview"
class X {
  void test(int i) {
    switch<caret> ("1" + (--i)) {
      case "2" -> System.out.println("2");
      default -> System.out.println("1");
    }
  }
}