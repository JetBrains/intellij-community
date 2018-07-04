// "Fix all 'Simplify stream API call chains' problems in file" "true"

import java.util.*;

class Test {
  public void test(List<String> list, Set<String> set, Iterable<String> it) {
    if(set.containsAll(list)) {
      System.out.println("all matched");
    }
    if(list.containsAll(set)) {
      System.out.println("all matched");
    }
      /*one*/
      if(((Collection<String>/*two*/) it).containsAll(set)) {
      System.out.println("all matched");
    }
  }

  static class MyList extends ArrayList<String> {
    @Override
    public boolean containsAll(Collection<?> c) {
      // do not replace when this-reference is used as infinite recursion might be produced
      return c.stream().allMatch(this::contains);
    }
  }
}