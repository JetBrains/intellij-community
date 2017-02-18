// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private Map<Integer, List<String>> test(String... list) {
      HashMap<Integer, List<String>> map = Arrays.stream(list).filter(Objects::nonNull).collect(Collectors.groupingBy(String::length, HashMap::new, Collectors.mapping(String::trim, Collectors.toList())));
      return map;
  }

  public static void main(String[] args) {
    System.out.println(new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs", "dd"));
  }
}
