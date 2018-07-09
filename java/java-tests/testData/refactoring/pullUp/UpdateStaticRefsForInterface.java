interface Foo {}
class FooImpl implements Foo {
  {
    foo();
  }
  
  public static void foo(){}
  <caret>
}
class U {
  public static void main(String[] args) {
    FooImpl.foo();
  }
}