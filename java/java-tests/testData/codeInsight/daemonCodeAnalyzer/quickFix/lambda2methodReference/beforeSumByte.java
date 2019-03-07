// "Replace lambda with method reference" "true"
class Bar {
  interface Foo {
    int sum(Byte b1, Byte b2);
  }
  
  public void test(Object obj) {
    Foo foo = (b1, b2) -> b1<caret> + b2;
  }
}