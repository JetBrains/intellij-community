
import java.util.Map;

class Test<T, K> {

  interface IA {
    void a();
  }

  interface IB<T> {
    void b(T t);
  }

  void onEntry(IA i){
    System.out.println(i);
  }
  void onEntry(IB<Map<T, K>> i){
    System.out.println(i);
  }

  void foo(Test t) {
    t.onEntry(this::fooBar);
  }

  private void fooBar() {}
}