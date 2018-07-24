// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.*;

public class Main {
  public long testCount(List<String> list) {
      long count = 0L;
      Set<String> uniqueValues = new HashSet<>();
      for (String s: list) {
          if (uniqueValues.add(s)) {
              count++;
          }
      }
      return count;
  }

  private static List<Object> testToList(List<? extends Number> numbers) {
      List<Object> list = new ArrayList<>();
      Set<Number> uniqueValues = new HashSet<>();
      for (Number number: numbers) {
          if (uniqueValues.add(number)) {
              list.add(number);
          }
      }
      return list;
  }

  public static void main(String[] args) {
    System.out.println(testToList(Arrays.asList(1,2,3,5,3,2,2,2,1,1,4,3)));
  }
}