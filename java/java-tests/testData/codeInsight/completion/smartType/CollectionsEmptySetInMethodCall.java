class Set<T> {}

class Collections {
  static <T> Set<T> emptySet() {}
}

class Bar {

  {
    foo(Collections.e<caret>)
  }

  void foo(Set<String> set){}
}