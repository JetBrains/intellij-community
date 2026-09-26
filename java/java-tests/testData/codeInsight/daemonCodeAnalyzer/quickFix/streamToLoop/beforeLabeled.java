// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.stream.Stream;

public class Main {
  void test() {
    test:
    Stream<caret>.of("foo", "bar", "baz").forEach(System.out::println);

    String s = "xyz";
    System.out.println(s);
  }
}