// "Replace Stream API chain with loop" "true"

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
    List<String> xyz = MyStream.of(data)
      .flatMap(s -> Stream.of(s, s + s)).takeWhile(s -> !s.isEmpty())
      .dropWhile(s -> s.length() < 3).col<caret>lect(Collectors.toList());
    System.out.println(xyz);
  }
}
