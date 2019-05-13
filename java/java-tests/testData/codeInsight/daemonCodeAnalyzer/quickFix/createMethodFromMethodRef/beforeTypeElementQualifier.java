// "Create method 'fooBar'" "true"
class FooBar {
  {
    Runnable r = Container<String>::foo<caret>Bar;
  }
}
class Container<T>{}
