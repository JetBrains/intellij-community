// "Fix all 'Simplify stream API call chains' problems in file" "true"

import java.util.*;

class Test {
  public void test(List<String> list, Set<String> set, Iterable<String> it) {
    if(list.stream().al<caret>lMatch(set::contains)) {
      System.out.println("all matched");
    }
    if(set.stream().allMatch(x -> list.contains(x))) {
      System.out.println("all matched");
    }
    if(set.stream()./*one*/allMatch(((Collection<String>/*two*/)it)::contains)) {
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