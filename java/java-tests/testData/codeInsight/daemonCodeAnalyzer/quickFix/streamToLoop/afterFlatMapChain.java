// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class Main {
  private static long test(List<? extends String> list) {
      long count = 0L;
      for (Object o : Arrays.asList(0, null, "1", list)) {
          for (Object o1 : Arrays.asList(o)) {
              for (Object o2 : Arrays.asList(o1)) {
                  for (Object o3 : Arrays.asList(o2)) {
                      for (Object o4 : Arrays.asList(o3)) {
                          for (Object o5 : Arrays.asList(o4)) {
                              count++;
                          }
                      }
                  }
              }
          }
      }
      return count;
  }

  public static void main(String[] args) {
    test(Arrays.asList("aa", "bbb", "c", null, "dd"));
  }
}