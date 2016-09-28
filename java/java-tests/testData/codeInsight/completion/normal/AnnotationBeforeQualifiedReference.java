@interface AbcdAnno {}

class Foo {
  void foo(@Abc<caret>Foo.Bar f) {}
  static class Bar{}
}