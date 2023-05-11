// "Unwrap 'switch'" "true-preview"
class X {
  void test(int i) {
    int x;
    <caret>switch ("1" +  (x= ((--i/*text*/) + "1" + (++i) + "1"))) {
      default -> System.out.println("1");
    };
  }
}