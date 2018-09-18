// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;
import java.util.stream.Stream;

public class Main {
  void test() {
      for (String s : Arrays.asList("foo", "bar", "baz")) {
          System.out.println(s);
      }

      String s = "xyz";
    System.out.println(s);
  }
}