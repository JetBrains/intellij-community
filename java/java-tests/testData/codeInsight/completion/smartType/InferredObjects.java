class M<K, V> {}
class HM<K, V> extends M<K, V> {}
class Foo {
  {
    M<String, Object> m = new <caret>
  }
}