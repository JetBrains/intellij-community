// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class A {
  public List<? super Integer> sum() {
      List<? super Integer> result = IntStream.range(0, 10).mapToObj(i -> i * 2).collect(Collectors.toList());
      return result;
  }
}