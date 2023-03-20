// "Replace with 'instanceof Integer'" "true-preview"
class X {
  void test(Object obj) {
    if(Integer.class.isIns<caret>tance(obj)) {
      System.out.println("Integer");
    }
  }
}