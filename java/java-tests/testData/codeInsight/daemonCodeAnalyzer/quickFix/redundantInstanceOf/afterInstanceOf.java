// "Replace with a null check" "true"
class Test {
  void test(String s) {
    Object obj = s;
    if(obj != null) {
      System.out.println("always");
    }
  }
}