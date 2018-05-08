// "Collect to 'LinkedHashSet'" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  public void test(List<String> list) {
    Set<String> set = list.stream().filter(Objects::nonNull).sorte<caret>d().collect(Collectors.toSet(/*set!*/));
    System.out.println(set);
  }
}
