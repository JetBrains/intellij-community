// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private Map<Integer, ArrayList<String>> test(String... list) {
      Map<Integer, ArrayList<String>> map = Arrays.stream(list).filter(Objects::nonNull).collect(Collectors.groupingBy(String::length, Collectors.toCollection(ArrayList::new)));
      return map;
  }

  public static void main(String[] args) {
    System.out.println(new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs", "dd"));
  }
}
