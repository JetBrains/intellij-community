// "Replace with 'instanceof Integer'" "true-preview"
class X {
  void test(Object obj) {
    if(obj instanceof Integer) {
      System.out.println("Integer");
    }
  }
}