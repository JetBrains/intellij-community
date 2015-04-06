class Foo {
  {
    Bar<Baz> b = method(new Bar<>());
  }

  static <T> T method(Class<T> t)  {
    return null;
  }

  static <T> T method(T t)  {
    return t;
  }
}

class Bar<T extends Enum<T>> {

}

enum Baz {}
