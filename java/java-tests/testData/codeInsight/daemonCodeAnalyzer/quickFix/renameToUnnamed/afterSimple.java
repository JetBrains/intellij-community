// "Rename 'x' to '_'" "true-preview"
class Simple {
  void test() {
    for(int _ : new int[10]) {
      System.out.println("hello");
    }
  }
}