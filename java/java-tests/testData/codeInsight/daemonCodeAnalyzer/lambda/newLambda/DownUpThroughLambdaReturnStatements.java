import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;


import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

class Main {
  public static void main(String...args){
    int[] array = new int[]{1,2,3,4,5,5,6};
    System.out.println(getMode(array));
  }

  private static Map.Entry<Integer, Long> getMode(int... src){
    return Arrays.stream(src)
      .mapToObj(Integer::valueOf)
      .collect(groupingBy(i -> i, counting()))
      .entrySet()
      .stream()
      .max((e1, e2) -> e1.getValue().compareTo(e2.getValue()))
      .flatMap(e -> Optional.of
        (new AbstractMap.SimpleImmutableEntry<>(Integer.valueOf(e.getKey().intValue()), Long.valueOf(e.getValue().longValue()))))
      .get();
  }
}

class Main1 {

  private static Kl<Integer> getMode(Optional<Map.Entry<Integer, Long>> max){
    return max
      .flatMap(e -> {
        return Optional.of(Kl.factory(e.getKey()));
      }).get();
  }

  static class Kl<A> {
    static <A1> Kl<A1> factory(A1 a) {
      return null;
    }
  }
}

class SameCalls<ST> {
  <B> List<B> bar(B a) {return null;}
  <R> Optional<R> foo(Function<? super ST, Optional<R>> computable) {return null;}

  List<String> ff(SameCalls<List<String>> sc){
    return sc.foo((x) -> {
      return Optional.of(bar(x.get(0)));
    }).get();
  }
}