// "Replace with 'instanceof Integer'" "false"
class X {
  void test(String obj) {
    if(Integer.class.isIns<caret>tance(obj)) {
      System.out.println("Integer");
    }
  }
}