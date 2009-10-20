class Key<T> {}
class Foo {
  <T> void putUserData(Key<T> k, T t) {}

  <T> Key<T> getKey(Key<T> t) {}

  {
    Key<Key<String>> k = new Key<Key<String>>();
    putUserData(k, getKey(new <caret>));
  }
}
