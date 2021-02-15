import java.util.function.Consumer;

class MyTest {
  void m(Provider provider) {
    provider.provide(new <error descr="Class 'Anonymous class derived from Consumer' must either be declared abstract or implement abstract method 'accept(T)' in 'Consumer'">Consumer<></error>() {
      <error descr="Method does not override method from its superclass">@Override</error>
      public void accept(String s) { }
    });
  }
  static class Provider<T> {
    void provide(Consumer<String> consumer){}
  }
}
