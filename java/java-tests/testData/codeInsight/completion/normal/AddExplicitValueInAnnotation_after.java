@interface Anno {
  String value();
  String bar();
  String goo();
}

@Anno(value = "a", bar = <caret>)
class Foo {}