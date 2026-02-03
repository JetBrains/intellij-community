// "Replace Stream API chain with loop" "true-preview"

import java.util.*;
import java.util.stream.*;

public class Main {
  private static void test(List<String> test) {
    System.out.println("x"+test.stream().c<caret>ollect(Collectors.joining())+"y");
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("a", "b", "c")));
  }
}