@interface Anno {
  String value();
  String bar();
  String goo();
}

@Anno("a", <caret>)
class Foo {}