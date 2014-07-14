import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

class Test {

  public static void main(String[] args) {

    List<String> list = new ArrayList<>(); // JDK 7 diamond operator
    list.add("aaa");
    list.add("bb");
    list.add("cccc");
    list.add("dd");
    list.add("e");

    schwartz(list.stream(), s -> s.length())
      .forEach(x -> { System.out.println(x); });
  }

  public static<T, R extends Comparable<? super R>> Stream<T> schwartz(Stream<T> stream, Function<T, R> f) {

    // class Pair - type of second element of pair must be Comparable
    final class Pair<F, S extends Comparable<? super S>> {
      public final F first;
      public final S second;
      public Pair(F first, S second){ this.first = first; this.second = second; }
    }

    return stream
      .map(t -> new Pair<>(t, f.apply(t)))
      .sorted((p1, p2) -> p1.second.compareTo(p2.second))
      .map(p -> p.first);
  }
}