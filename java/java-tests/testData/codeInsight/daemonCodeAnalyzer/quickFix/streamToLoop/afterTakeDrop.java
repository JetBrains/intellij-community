// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  // Mock Java 9 methods with inheritor
  interface MyStream<T> extends Stream<T> {
    <R> MyStream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper);

    MyStream<T> takeWhile(Predicate<? super T> predicate);

    MyStream<T> dropWhile(Predicate<? super T> predicate);

    static <T> MyStream<T> of(List<T> list) {
      return null;
    }
  }

  public static void test(List<String> data) {
      List<String> xyz = new ArrayList<>();
      boolean dropping = true;
      OUTER:
      for (String s : data) {
          for (String s1 : Arrays.asList(s, s + s)) {
              if (s1.isEmpty()) {
                  break OUTER;
              }
              if (dropping) {
                  if (s1.length() < 3) {
                      continue;
                  }
                  dropping = false;
              }
              xyz.add(s1);
          }
      }
      System.out.println(xyz);
  }
}
