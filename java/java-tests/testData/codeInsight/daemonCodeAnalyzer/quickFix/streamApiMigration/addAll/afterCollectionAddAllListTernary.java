// "Replace with collect" "true-preview"
import java.util.*;
import java.util.stream.Collectors;

public class Test {
  List<String> test(List<List<String>> list) {
    List<String> result = list.stream().filter(Objects::nonNull).flatMap(nested -> (nested.isEmpty() ? Collections.singleton("foo") : nested).stream()).collect(Collectors.toList());
      return result;
  }
}
