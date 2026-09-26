import java.util.*;
import java.util.stream.*;

class T {
  void test() {
    Collections.unmodifiableSet(Arrays.asList("US", "DE")
                                  .stream().map(String::toLowerCase).collect(Collectors.toCollection(TreeSet::new)));
  }
}