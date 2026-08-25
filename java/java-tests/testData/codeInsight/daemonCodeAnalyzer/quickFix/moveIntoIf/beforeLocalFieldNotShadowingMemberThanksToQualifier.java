// "Move up into 'if' statement branches" "true-preview"
class Test {
  String[] field;
  
  void test(int x) {
    Test foo = new Test();
    if (x > 0) {
      String field = "foo";
      System.out.println(field);
    }
    <selection>this.field = new String[]{"One", "two"};
    Test.this.field =  new String[]{"Three", "Four"}; 
    foo.field = new String[]{"Four, Five"}; </selection>
  }
}