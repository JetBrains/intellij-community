// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class Main {
  public void testCast(Object obj, List<Object> list) {
      for (Number n : ((Iterable<Number>) obj)) {
          list.add(n);
      }
  }

  public static void main(String[] args) {
    List<String> list = Arrays.asList("a", "b");
      for (String s : list) {
          System.out.println(s);
      }
  }
}