// "Replace with a null check" "true-preview"
class Test {
  void test(String s) {
    Object object = s;
    if(object != null) {
      System.out.println("always");
    }
  }
}