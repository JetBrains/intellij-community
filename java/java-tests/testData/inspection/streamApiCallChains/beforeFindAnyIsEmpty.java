// "Replace with 'isEmpty()'" "true-preview"

import java.util.*;

class FindAnyIsEmpty {
  public static void test(List<String> list) {
    if(list.stream().findAny().isEm<caret>pty()) {
      System.out.println("oops");
    }
  }
}