import java.io.Serializable;
import java.util.*;

class Test {
  public static <K extends Comparable<? super K>, V> Comparator<Map.Entry<K, V>> foo() {
    return (Comparator<Map.Entry<K, V>> & Serializable)(c1, c2) -> c1.getKey().compareTo(c2.getKey());
  }
  public static void main(String... args) {
    var shouldCompile = (Consumer<Integer> & IntConsumerAdapter1)
      i -> System.out.println("Consuming" + i);
    System.out.println(shouldCompile.getClass());
    shouldCompile.accept(42);
    shouldCompile.accept(Integer.valueOf(52));

    var shouldNotCompile = (Consumer<Integer> & IntConsumerAdapter2)
      <error descr="No target method found">i -> System.out.println("Consuming " + i)</error>;
    shouldNotCompile.accept(42);
    shouldNotCompile.accept(Integer.valueOf(52));
  }

  public interface Consumer<T> {
    void accept(T t);
  }

  public interface IntConsumer {

    void accept(int value);
  }

  @FunctionalInterface
  interface IntConsumerAdapter1 extends IntConsumer, Consumer<Integer> {
    default void accept(int value) {
      accept(Integer.valueOf(value));
    }
  }

  interface IntConsumerAdapter2 extends IntConsumer, Consumer<Integer> {
    default void accept(int value) {
      accept(Integer.valueOf(value));
    }

    default void accept(Integer integer) {
    }
  }
}
