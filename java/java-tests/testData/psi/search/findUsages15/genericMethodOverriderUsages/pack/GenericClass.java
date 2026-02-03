package pack;

class GenericClass<T> {
  void foo (T t) {}
}

class GenericClassDerived extends GenericClass<String> {
  void foo (String s) {}

  void bar () {
    foo ("");
  }
}