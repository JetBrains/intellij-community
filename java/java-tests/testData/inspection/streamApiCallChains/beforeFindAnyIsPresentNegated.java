// "Replace with 'isEmpty()'" "true-preview"

import java.util.*;

class FindAnyIsPresentNegated {
  public static void test(List<String> list) {
    if(!list.stream().findAny().isPre<caret>sent()) {
      System.out.println("oops");
    }
  }
}