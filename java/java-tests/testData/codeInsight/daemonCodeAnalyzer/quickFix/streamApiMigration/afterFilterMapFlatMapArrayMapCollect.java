// "Replace with collect" "true"
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {
  public void test(Map<String, String[]> map) {
      List<String> result = map.entrySet().stream().filter(entry -> entry.getKey().startsWith("x")).map(Map.Entry::getValue).flatMap(Arrays::stream).map(String::trim).collect(Collectors.toList());
  }
}