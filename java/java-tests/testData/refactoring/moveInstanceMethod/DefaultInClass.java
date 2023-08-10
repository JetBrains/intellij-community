class Foo {
}
interface Bar {
  default void ba<caret>z(Foo foo) {}

  void grault(Foo foo);
}