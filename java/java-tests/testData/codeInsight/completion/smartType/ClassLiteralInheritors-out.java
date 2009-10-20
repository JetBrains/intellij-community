@interface Anno {
  Class<? extends Foo> value();
}
@Anno(Bar.class<caret>)
class Foo {}
class Bar extends Foo{}