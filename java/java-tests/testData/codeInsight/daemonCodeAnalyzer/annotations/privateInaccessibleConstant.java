@FooAnnotation(<error descr="'Foo.BAR' has private access in 'Foo'">Foo.BAR</error>)
class Foo {
  private static final String BAR = "bar";
}

@interface FooAnnotation {
  String value();
}