@interface Anno {
  Class<? extends String> value();
}
@Anno(String.cl<caret>)
class Foo {}