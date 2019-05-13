// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private static List<Object> test(List<? extends Number> numbers) {
      List<Object> list = new ArrayList<>();
      Set<Number> uniqueValues = new HashSet<>();
      for (Number number : numbers) {
          if (uniqueValues.add(number)) {
              list.add(number);
          }
      }
      return list;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(1,2,3,5,3,2,2,2,1,1,4,3)));
  }
}