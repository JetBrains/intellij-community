// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static void test(List<String> list) {
      boolean found = false;
      for (String x : list) {
          if (x != null) {
              if (x.startsWith("x")) {
                  found = true;
                  break;
              }
          }
      }
      if(found) {
      System.out.println("Ok!");
    }
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "b", "xyz"));
  }
}