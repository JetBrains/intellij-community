
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

abstract class Token {


  private static B<Long> getMode(Optional<Map.Entry<Integer, Long>> max){
    return  max
      .flatMap(e -> Optional.of(new B<>(Long.valu<ref>eOf(e.getValue().longValue()))))
      .get();
  }

  static class B<K> {
    public B(K k) {}
  }
}