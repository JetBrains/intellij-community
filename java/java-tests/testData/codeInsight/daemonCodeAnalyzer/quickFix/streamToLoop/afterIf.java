// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static void test(List<String> list) {
      for (String s : list) {
          if (s != null) {
              if (s.startsWith("x")) {
                  System.out.println("Ok!");
                  break;
              }
          }
      }
      if(list.size() > 2) {
        for (String x : list) {
            if (x != null) {
                if (x.startsWith("x")) {
                    System.out.println("Ok!");
                    break;
                }
            }
        }
    }
    if(list.size() > 2) {
        boolean b = false;
        for (String x : list) {
            if (x != null) {
                if (x.startsWith("x")) {
                    b = list.size() < 10;
                    break;
                }
            }
        }
        if (b) {
            System.out.println("Ok!");
        }
    }
    if(list.size() > 2 || list.stream().filter(x -> x != null).anyMatch(x -> x.startsWith("x"))) { // not supported
      System.out.println("Ok!");
    }
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "b", "xyz"));
  }
}