@interface Ann {
  String value();
}

@Ann(Foo.BAR)
class Foo {
    protected static final String BAR = "bar";
}