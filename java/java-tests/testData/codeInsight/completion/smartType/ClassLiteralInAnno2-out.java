@interface Anno {
  Class<? extends String> value();
}
@Anno(String.class<caret>)
class Foo {}