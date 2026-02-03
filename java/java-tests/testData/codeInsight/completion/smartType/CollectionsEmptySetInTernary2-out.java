class Set<T> {}

class Collections {
  static <T> Set<T> emptySet() {}
}

class Bar {

  Set<String> bar() {
    return true ? null : Collections.<String>emptySet();<caret>
  }

  void foo(Set<String> set){}
}