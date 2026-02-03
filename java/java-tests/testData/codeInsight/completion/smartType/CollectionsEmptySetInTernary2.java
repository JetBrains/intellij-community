class Set<T> {}

class Collections {
  static <T> Set<T> emptySet() {}
}

class Bar {

  Set<String> bar() {
    return true ? null : Collections.e<caret>
  }

  void foo(Set<String> set){}
}