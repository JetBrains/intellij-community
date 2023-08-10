import java.util.*;

class Test {
  {
    List<String> strings = Arrays.asList("a", "a");
    System.out.println(strings.stream().allMatch(e -> new HashSet<>().add(e)));
  }
}