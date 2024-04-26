// "Remove switch label" "true-preview"
class X {
  void test(Object obj) {
    switch (obj) {
      case String s when s.length()<caret> < 0:
        System.out.println("oops");
        break;
      default:
        System.out.println("something else");
        break;
    }
  }
}