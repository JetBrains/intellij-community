class Test<V> {

  void bar(Win window, I<V> consumer) {}
  void bar(Comp component, I<V> consumer) {}

  private void foo(final Win component, final I<V> consumer) {
    bar(component, consumer);
  }
}

interface I<K>{}
class Comp {}
class Win extends Comp {}