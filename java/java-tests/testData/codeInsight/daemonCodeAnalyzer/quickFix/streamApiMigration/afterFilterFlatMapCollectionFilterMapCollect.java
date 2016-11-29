// "Replace with collect" "true"
import java.util.*;
import java.util.stream.Collectors;

public class Main {
  public void test(List<Set<String>> nested) {
      List<String> result = nested.stream().filter(Objects::nonNull).flatMap(Collection::stream).filter(str -> str.startsWith("xyz")).map(String::trim).collect(Collectors.toList());
  }
}