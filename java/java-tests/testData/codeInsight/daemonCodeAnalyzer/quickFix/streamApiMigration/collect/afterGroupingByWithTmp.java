// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;

public class Main {
  Map<Integer, List<String>> test(List<String> list) {
      Map<Integer, List<String>> map = list.stream().collect(Collectors.groupingBy(String::length));
      return map;
  }

  public static void main(String[] args) {
    System.out.println(new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs", "dd"));
  }
}
