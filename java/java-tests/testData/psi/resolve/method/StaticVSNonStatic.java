 public class Foo {
  public void foo() {}

  public static void foo(char c){}
}

class A{
  {
    Foo.<ref>foo();
  }
}