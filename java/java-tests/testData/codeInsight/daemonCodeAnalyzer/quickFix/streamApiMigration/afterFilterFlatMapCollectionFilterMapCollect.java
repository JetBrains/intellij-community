// "Replace with collect" "true"
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {
  public void test(List<Set<String>> nested) {
    List<String> result = nested.stream().filter(element -> element != null).flatMap(Collection::stream).filter(str -> str.startsWith("xyz")).map(String::trim).collect(Collectors.toList());
  }
}