// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
  private static Number[] test(Object[] objects) {
      List<Object> list = new ArrayList<>();
      for (Object object : objects) {
          if (object instanceof Number) {
              list.add(object);
          }
      }
      return list.toArray(new Number[0]);
  }

  public static void main(String[] args) {
    System.out.println(Arrays.asList(test(new Object[]{1, 2, 3, "string", 4})));
  }
}