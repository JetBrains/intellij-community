@interface Anno {
  Class<? extends Foo> value();
}
@Anno(B<caret>)
class Foo {}
class Bar extends Foo{}