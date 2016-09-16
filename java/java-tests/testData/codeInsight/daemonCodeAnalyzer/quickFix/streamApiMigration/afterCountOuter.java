// "Replace with count()" "true"
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Main {
  public void test(List<Set<String>> nested) {
      int count = (int) nested.stream().filter(Objects::nonNull).flatMap(Collection::stream).filter(str -> str.startsWith("xyz")).count();
  }
}