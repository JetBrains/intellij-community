// "Create method 'fooBar'" "true"
interface I {
  String str(Container c);
}
class FooBar {
  {
    I i = Container::foo<caret>Bar;
  }
}
class Container{}
