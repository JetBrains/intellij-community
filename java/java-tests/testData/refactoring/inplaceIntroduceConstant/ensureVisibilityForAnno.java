@interface Ann {
  String value();
}

@Ann("b<caret>ar")
class Foo {
}