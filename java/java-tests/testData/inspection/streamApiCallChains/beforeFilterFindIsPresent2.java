// "Replace 'filter().findFirst().isPresent()' with 'anyMatch()'" "true-preview"

import java.util.*;
import java.util.stream.Stream;

class FilterFindIsPresent {
  public static void test(List<String> list) {
    if(list.stream().filter(l -> !l.isEmpty()).findFirst().isPre<caret>sent()) {
      System.out.println("oops");
    }
  }
}