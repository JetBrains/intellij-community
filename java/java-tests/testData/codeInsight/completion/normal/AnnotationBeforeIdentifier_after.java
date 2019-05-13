@interface AbcdAnno {}

class Foo {
  void foo(@AbcdAnno<caret> Foo f) {}
}