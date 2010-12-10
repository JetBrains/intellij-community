public class FooMap<K,V extends K> implements YourMapInterface<K, V> {
  void foo() {
    class Zoooooooo {}
    Zoooooooo<caret>
  }
}