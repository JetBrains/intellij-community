// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;

public class Main {
  public void test(List<Set<String>> nested) {
      /*non-equal*//*empty*/
      List<String> result = nested.stream().filter(Objects::nonNull).flatMap(Collection::stream).filter(str -> str./*startswith*/startsWith("xyz")).map(String::trim).collect(Collectors.toList());
      // 1
      /*target is here*/
      // 2
      // 3
      // 4
  }
}