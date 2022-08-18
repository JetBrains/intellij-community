// "Replace with collect" "true-preview"
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Main {
  private Map<String, Integer> test(String... list) {
    Map<String, Integer> map = Arrays.stream(list).filter(Objects::nonNull).collect(Collectors.toMap(s -> s, s -> 1, Integer::sum));
      return map;
  }

  public static void main(String[] args) {
    System.out.println(new Main().test("a", "bbb", null, "cc", "dd", "eedasfasdfs", "dd"));
  }
}
