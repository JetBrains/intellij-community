// "Move up into 'if' statement branches" "true-preview"
class Test {
  String[] field;
  
  void test(int x) {
    if (x > 0) {
      String field2 = "foo";
      System.out.println(field2);
    }
    f<caret>ield = new String[]{"One", "two"};
  }
}