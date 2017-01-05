// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  static class MyList<T, X> extends ArrayList<X> {}

  public List<CharSequence> getList() {
    return Collections.emptyList();
  }

  public MyList<? extends Number, CharSequence> createList() {
    return new MyList<>();
  }

  private void collect() {
      Map<Integer, MyList<? extends Number, CharSequence>> result = new HashMap<>();
      for (CharSequence x : getList()) {
          result.computeIfAbsent(x.length(), k -> createList()).add(x);
      }
      Map<Integer, ? extends MyList<? extends Number, ? extends CharSequence>> map =
              result;
    System.out.println(map);
  }
