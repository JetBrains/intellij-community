// "Replace with enhanced 'switch' statement" "true-preview"

class NotDefenitessignment1 {
  void test(int x) {
    String s;
    if (Math.random() > 0.5) {
      s = "bar";
    }
      switch (x) {
          case 1 -> s = "baz";
          case 2 -> s = "qux";
      }
  }
}