public class Foo {
  void method(MyEnum e) { }

  {
    method(<caret>);
  }


}

enum MyEnum { foo, bar }