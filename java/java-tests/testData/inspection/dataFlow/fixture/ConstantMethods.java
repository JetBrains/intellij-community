// IDEA-191876
class Test {
  void test(String s) {
    int x = s.indexOf("foo")+"foo".length();
    if (<warning descr="Condition 'x == -1' is always 'false'">x == -1</warning>) {
      System.out.println("Impossible");
    }
  }

  void test2() {
    for(int i=0; i<3; i++){ // loop is unrolled
      String s = String.valueOf(i);
      if(<warning descr="Condition 's.length() > 1' is always 'false'">s.length() > 1</warning>) {
        System.out.println("Impossible");
      }
    }
  }

  void test3(boolean b) {
    String s = b ? "Hello" : "World";
    if(<warning descr="Condition 's.indexOf('o') == 1 && b' is always 'false'">s.indexOf('o') == 1 && <warning descr="Condition 'b' is always 'false' when reached">b</warning></warning>) {
      System.out.println("Impossible");
    }
  }
}