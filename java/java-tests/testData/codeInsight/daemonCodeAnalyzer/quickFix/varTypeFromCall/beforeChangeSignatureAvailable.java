// "Change variable 'var' type to 'Foo" "false"
public class Test {
  void foo()  {
    final Foo var = new Foo();
    var.foo("", 6<caret>6);
  }
}

class Foo {
  void foo(String str, String str1){}
}