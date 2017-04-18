
import java.util.Map;
import java.util.function.Consumer;
class MyTest {

  static void createClass(Map<String, Object> templateAttributes,
                          final Consumer<Integer> postProcessRunnable) {
  }

  void f(Map<String, String> m) {
    final Ref<Integer> clazz = new Ref<>();
    createClass(
      new HashMap<>(foo(m)),
      i -> clazz.set(i)
    );
  }

  private Map<String, String> foo(final Map<String, String> m) {
    return null;
  }

  private class Ref<T> {
    void set(T t) {
    }
  }
}
class HashMap<K, V> extends java.util.HashMap<K, V> {
  public <K1 extends K, V1 extends V> HashMap(Map<? extends K1, ? extends V1> map) {
    super(map);
  }
}