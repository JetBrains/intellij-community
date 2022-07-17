// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;

class Test {
  List<String> test(List<List<String>> list) {
    List<String> result = list.stream().flatMap(Collection::stream).collect(Collectors.toList());
      return Collections.unmodifiableList(result);
  }
}