// "Replace Stream.filter().findFirst().isPresent() with Stream.anyMatch()" "true"

import java.util.*;
import java.util.stream.Stream;

class FilterFindIsPresent {
  public static void test(List<String> list) {
    if(list.stream().filter(l -> !l.isEmpty()).findFirst().isPre<caret>sent()) {
      System.out.println("oops");
    }
  }
}