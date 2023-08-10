import java.util.*;

class Test {
  {
    List<String> strings = Arrays.asList("a", "a");
      HashSet<Object> <caret>objects = new HashSet<>();
      System.out.println(strings.stream().allMatch(e -> objects.add(e)));
  }
}