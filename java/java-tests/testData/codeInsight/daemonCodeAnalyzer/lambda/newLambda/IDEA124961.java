import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

class Test {

  private static void list(List<? extends CharSequence> l) {
    Stream<? extends CharSequence> str = map(() -> l.get(0));
    Stream<? extends CharSequence> str1 = map1(() -> l.get(0));
    Stream<? extends CharSequence> str2 = map1(() -> l.get(0));
  }

  static <M> Stream<M> map (Supplier<? extends M> mapper) { return null;}
  static <M> Stream<M> map1(Supplier<? super M> mapper) { return null;}
  static <M> Stream<M> map2(Supplier<M> mapper) { return null;}
}
