class Key<T> {
  static <T> Key<T> create(String s);
}

class Foo {
  <T> void putUserData(Key<T[]> key, T value) {}

  {
    putUserData(<caret>)
  }

}