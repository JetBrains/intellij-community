// "Replace Stream API chain with loop" "true"

import java.util.List;

import static java.util.Arrays.asList;

public class Main {
  public static void test(List<List<String>> list) {
      boolean b = true;
      OUTER:
      for (List<String> x : list) {
          if (x != null) {
              for (String s : x) {
                  if (!s.startsWith("a")) {
                      b = false;
                      break OUTER;
                  }
              }
          }
      }
      if(b) {
      System.out.println("ok");
    }
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList(), asList("a"), asList("b", "c"))));
    System.out.println(test(asList(asList(), asList("d"), asList("b", "c"))));
  }
}