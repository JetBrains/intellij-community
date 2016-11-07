// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class Main {
  private static long test(List<? extends String> list) {
      long count = 0;
      for (Object o : Arrays.<Object>asList(0, null, "1", list)) {
          for (Object o1 : Arrays.<Object>asList(o)) {
              for (Object o2 : Arrays.<Object>asList(o1)) {
                  for (Object o3 : Arrays.<Object>asList(o2)) {
                      for (Object o4 : Arrays.<Object>asList(o3)) {
                          for (Object o5 : Arrays.<Object>asList(o4)) {
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