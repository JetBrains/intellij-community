// "Collapse into loop" "true-preview"
class X {
  void test() {
      for (int i : new int[]{1, 2, 3, 5, 8}) {
          System.out.println(i);
      }
  }
}