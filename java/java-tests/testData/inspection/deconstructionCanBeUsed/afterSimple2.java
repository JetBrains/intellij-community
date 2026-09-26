// "Replace with record pattern" "true-preview"
class X {
  record Point(int x, int y) {
  }

  void test(Object obj) {
    if (obj instanceof Point(int l, int m)) {
        System.out.println(l + m);
    }
  }
}
