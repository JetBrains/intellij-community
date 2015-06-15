import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collector;

class Test {
  public static <T5> Collector<T5, Set<T5>, Set<T5>> foo(Supplier<Set<T5>> setConstructor,
                                                         BinaryOperator<Set<T5>> rBinaryOperator) {

    return Collector.of(setConstructor, Set<T5>:: add, rBinaryOperator);
  }

  public static <T> Collector<T, Set<T>, Set<T>> foo() {
    final Supplier<Set<T>> setConstructor = HashSet::new;

    return Collector.of(
      setConstructor,
      Set<T>::add,
      (Set<T> s1, Set<T> s2) -> {
        s1.addAll(s2);
        return s1;
      }
    );
  }
}