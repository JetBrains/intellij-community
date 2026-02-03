public class Foo {
  void m(Object p) {
    m(Foo.new<caret>)
  }
}