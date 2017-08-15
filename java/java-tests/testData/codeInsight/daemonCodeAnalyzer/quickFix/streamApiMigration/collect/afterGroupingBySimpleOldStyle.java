// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private Map<Integer, List<String>> test(String... list) {
    Map<Integer, List<String>> map = new HashMap<>();
      map = Arrays.stream(list).filter(Objects::nonNull).collect(Collectors.groupingBy(String::length, Collectors.toCollection(ArrayList::new)));
    return map;
  }

  public static void main(String[] args) {
    System.out.println(new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs", "dd"));
  }
}
