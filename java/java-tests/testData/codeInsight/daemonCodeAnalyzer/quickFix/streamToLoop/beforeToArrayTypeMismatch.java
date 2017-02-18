// "Replace Stream API chain with loop" "true"

import java.util.stream.Stream;

public class Main {
  private static Number[] test(Object[] objects) {
    return Stream.of(objects).filter(Number.class::isInstance).toArr<caret>ay(Number[]::new);
  }

  public static void main(String[] args) {
    System.out.println(Arrays.asList(test(new Object[]{1, 2, 3, "string", 4})));
  }
}