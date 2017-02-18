// "Replace Stream.filter().findAny().isPresent() with Stream.anyMatch()" "true"

import java.util.*;
import java.util.stream.Stream;

class FilterFindIsPresent {
  public static void test(List<String> list) {
    if(list.stream().fi<caret>lter(l -> !l.isEmpty()).findAny().isPresent()) {
      System.out.println("oops");
    }
  }
}