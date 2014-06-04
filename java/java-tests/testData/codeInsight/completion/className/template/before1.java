import pack.Foo;
class Test {
  {
    m(new <caret>)
  }
  <T> void m(Foo<T> foo){}
}