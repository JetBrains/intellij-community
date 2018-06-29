interface Foo {
    static void foo(){}
}
class FooImpl implements Foo {
  {
    Foo.foo();
  }

}
class U {
  public static void main(String[] args) {
    Foo.foo();
  }
}