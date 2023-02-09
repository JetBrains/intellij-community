// "Collapse loop with stream 'toArray()'" "true-preview"
import java.util.*;

public class Test {
  String[] test(List<String> list) {
      return list.stream().filter(Objects::nonNull).sorted(String.CASE_INSENSITIVE_ORDER).toArray(String[]::new);
  }
}
