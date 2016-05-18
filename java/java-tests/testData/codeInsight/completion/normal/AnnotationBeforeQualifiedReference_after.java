@interface AbcdAnno {}

class Foo {
  void foo(@AbcdAnno<caret> Foo.Bar f) {}
  static class Bar{}
}