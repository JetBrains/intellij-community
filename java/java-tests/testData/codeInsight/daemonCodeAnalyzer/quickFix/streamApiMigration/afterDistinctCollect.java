// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  List<String> test(List<String> list) {
      List<String> result = list.stream().distinct().filter(s -> !s.contains("foo")).collect(Collectors.toList());
      return result;
  }
}
