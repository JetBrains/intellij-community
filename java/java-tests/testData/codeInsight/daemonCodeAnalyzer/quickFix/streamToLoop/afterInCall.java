// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.*;

public class Main {
  private static void test(List<String> test) {
      StringBuilder sb = new StringBuilder();
      for (String s : test) {
          sb.append(s);
      }
      System.out.println("x"+ sb.toString() +"y");
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("a", "b", "c")));
  }
}