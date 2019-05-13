// "Use 'Collectors.toConcurrentMap' collector" "true"
import java.util.*;
import java.util.stream.Collectors;

public class Test {
  public static void main(String[] args) {
    List<String> input = Arrays.asList("a", "bbb", "cc", "ddd", "  x", "ee");
    Map<Integer, String> map3 = input.stream().collect(Collectors.<caret>groupingByConcurrent(String::length, Collectors
      .collectingAndThen(Collectors.maxBy(Comparator.<String>naturalOrder()), Optional::get)));
    System.out.println(map3);
  }
}