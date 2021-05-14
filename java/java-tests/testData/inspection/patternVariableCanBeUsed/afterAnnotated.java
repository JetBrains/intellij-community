// "Replace 's' with pattern variable" "true"
class X {
  void test(Object obj) {
    if (obj instanceof @Foo String s) {
    }
  }
  
  @interface Foo {}
}