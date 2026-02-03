// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.List;
import java.util.stream.*;

public class Main {
  public long testCount(List<String> list) {
    return list.stream().distinct().c<caret>ount();
  }

  private static List<Object> testToList(List<? extends Number> numbers) {
    return numbers.stream().distinct().collect(Collectors.toList());
  }

  public static void main(String[] args) {
    System.out.println(testToList(Arrays.asList(1,2,3,5,3,2,2,2,1,1,4,3)));
  }
}