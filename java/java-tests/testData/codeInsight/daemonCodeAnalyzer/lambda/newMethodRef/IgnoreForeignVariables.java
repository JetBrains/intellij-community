
import java.util.function.Function;

class SomeClass<K, V> {

  SomeClass(Function<K, V> transformer) {}

  static <M, N> SomeClass<M, N> create(Function<M, N> t) {
    return null;
  }

  static void someMethod() {
    final SomeClass<MyBean, String> instance = create(MyBean::overloadedMethod);
    final SomeClass<MyBean, String> instance1 = new SomeClass<>(MyBean::overloadedMethod);
  }
}
class MyBean {

  private String overloadedMethod(String param) {
    return param;
  }

  public String overloadedMethod() {
    return overloadedMethod(null);
  }

}