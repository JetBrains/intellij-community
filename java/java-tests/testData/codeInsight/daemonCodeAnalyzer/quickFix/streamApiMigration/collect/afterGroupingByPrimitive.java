// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
  public void test() {
    String[] values = {"a", "b", "c"};
    Map<String, List<Object>> map = IntStream.range(0, values.length).boxed().collect(Collectors.groupingBy(i -> "X" + i, Collectors.mapping(i -> values[i], Collectors.toList())));
  }
}
