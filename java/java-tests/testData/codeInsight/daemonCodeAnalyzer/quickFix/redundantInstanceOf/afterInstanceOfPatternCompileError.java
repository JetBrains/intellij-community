// "Replace with a null check" "true"
class Test {
  void test(String s) {
    if(s != null) {
      System.out.println("always");
    }
  }
}