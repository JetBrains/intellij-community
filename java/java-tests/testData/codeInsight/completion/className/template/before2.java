import pack.Foo;
class Test {
  {
    m(new <caret>)
  }
  void m(Foo<String> foo){}
}