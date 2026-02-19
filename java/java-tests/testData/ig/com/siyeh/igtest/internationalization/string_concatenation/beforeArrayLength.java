// "Annotate field 'length' as @NonNls" "false"
class X {
  void test(String[] str) {
    String result = "foo" +<caret> str.length;  
  }
}

