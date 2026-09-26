// "Replace with '!isEmpty()'" "true-preview"

import java.util.*;

class FindFirstIsPresent {
  public static void test(List<String> list) {
    if(list.stream().findFirst().isPre<caret>sent()) {
      System.out.println("oops");
    }
  }
}