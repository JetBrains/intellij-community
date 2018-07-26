// "Replace Stream API chain with loop" "true"

import java.util.*;

public class Main {
  private static long test(List<?> list) {
      long count = 0L;
      Set<Object> uniqueValues = new HashSet<>();
      long toSkip = list.size() / 2;
      for (Object o : list) {
          if (toSkip > 0) {
              toSkip--;
              continue;
          }
          if (uniqueValues.add(o)) {
              count++;
          }
      }
      return count;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(1,2,3,3,2,1,1,2,3)));
  }
}