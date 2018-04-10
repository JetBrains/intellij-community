class Foo {
    public static void foo(){}
}
class FooImpl extends Foo {
  {
    foo();
  }

}
class U {
  public static void main(String[] args) {
    Foo.foo();
  }
}