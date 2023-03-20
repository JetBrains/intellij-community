// "Replace with record pattern" "true-preview"
class X {
  record Point(int x, int y) {
  }

  void test(Object obj) {
    if (obj instanceof Po<caret>int point) {
      int x = ((((point)).x()));
      System.out.println(x + ((((point)).y())));
    }
  }
}
