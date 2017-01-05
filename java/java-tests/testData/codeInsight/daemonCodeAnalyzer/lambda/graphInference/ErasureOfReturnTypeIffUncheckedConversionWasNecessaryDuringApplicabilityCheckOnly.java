import java.util.List;
import java.util.stream.Collectors;

class Test {
  private static List<Object> test(List<List> list) {
    return list.stream().flatMap(List::stream).collect(Collectors.toList());
  }
}
