// "Collect to 'LinkedHashSet'" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  public void test(List<String> list) {
      /*set!*/
      Set<String> set = list.stream().filter(Objects::nonNull).sorted().collect(Collectors.toCollection(LinkedHashSet::new));
    System.out.println(set);
  }
}
