// "Replace 's' with pattern variable" "true"
class X {
  void test(Object obj) {
    if (obj instanceof String) {
      @Foo String <caret>s = (String)obj;
    }
  }
  
  @interface Foo {}
}