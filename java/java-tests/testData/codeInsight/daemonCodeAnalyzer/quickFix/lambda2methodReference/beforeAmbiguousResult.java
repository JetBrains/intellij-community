// "Replace lambda with method reference" "false"
import java.util.*;
class IDEA100385 {
  void foo(N<Double> n, List<Double> l){
    n.forEach((double e) -> {
      l.ad<caret>d(e);
    });
  }
  static interface N<E> {
    default void forEach(DoubleConsumer consumer) {
    }
    void forEach(Consumer<? super E> consumer);
  }

  interface DoubleConsumer {
    void _(double d);
  }

  interface Consumer<T> {
    public void accept(T t);
  }

}
