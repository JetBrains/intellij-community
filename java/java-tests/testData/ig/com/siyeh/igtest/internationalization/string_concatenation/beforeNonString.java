// "Annotate parameter 'intValue' as @NonNls" "false"
class X {
  void test(int intValue) {
    String result = "foo" +<caret> intValue;  
  }
}

