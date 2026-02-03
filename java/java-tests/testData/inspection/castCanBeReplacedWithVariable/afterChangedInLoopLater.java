// "Replace '(String) obj' with 's'" "true"

class X {
  void test(Object obj) {
    for (int i = 0; i < 10; i++) {
      String s = (String) obj;
      System.out.println(s);
      System.out.println(s);
      s = null;
    }
  }
}