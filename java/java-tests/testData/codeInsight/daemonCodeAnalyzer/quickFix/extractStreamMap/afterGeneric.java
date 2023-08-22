// "Extract variable 'set' to 'map' operation" "true-preview"
import java.util.*;
import java.util.stream.*;

public class Test {
  void testMap(List<Map<String, String>> list) {
      list.stream().map(Map::keySet).flatMap(set -> set.stream()).forEach(System.out::println);
  }
}