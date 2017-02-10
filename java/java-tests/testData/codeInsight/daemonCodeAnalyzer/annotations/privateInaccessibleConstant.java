@FooAnnotation(Foo.<error descr="'BAR' has private access in 'Foo'">BAR</error>)
class Foo {
  private static final String BAR = "bar";
}

@interface FooAnnotation {
  String value();
}