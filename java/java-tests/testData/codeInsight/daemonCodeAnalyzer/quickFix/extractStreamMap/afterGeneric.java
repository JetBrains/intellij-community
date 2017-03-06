// "Extract variable 'set' to separate mapping method" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  void testMap(List<Map<String, String>> list) {
      list.stream().map(Map::keySet).flatMap(set -> set.stream()).forEach(System.out::println);
  }
}