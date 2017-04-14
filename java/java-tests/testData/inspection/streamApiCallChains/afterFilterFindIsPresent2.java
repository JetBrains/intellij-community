// "Replace 'filter().findFirst().isPresent()' with 'anyMatch()'" "true"

import java.util.*;
import java.util.stream.Stream;

class FilterFindIsPresent {
  public static void test(List<String> list) {
    if(list.stream().anyMatch(l -> !l.isEmpty())) {
      System.out.println("oops");
    }
  }
}