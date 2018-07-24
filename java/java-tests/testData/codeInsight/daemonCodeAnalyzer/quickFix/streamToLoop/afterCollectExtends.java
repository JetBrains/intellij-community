// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
  private List<? extends CharSequence> asList(CharSequence s) {
    return Collections.singletonList(s);
  }

  public List<? extends CharSequence> getListExtends() {
    return Collections.emptyList();
  }

  public List<CharSequence> getList() {
    return Collections.emptyList();
  }

  private void collect() {
      List<CharSequence> list = new ArrayList<>();
      for (CharSequence charSequence: getListExtends()) {
          if (charSequence != null) {
              list.add(charSequence);
          }
      }
      List<? extends CharSequence> res = list;
    System.out.println(res);
  }

  private void collect2() {
      List<List<? extends CharSequence>> list = new ArrayList<>();
      for (CharSequence charSequence: getListExtends()) {
          List<? extends CharSequence> charSequences = asList(charSequence);
          list.add(charSequences);
      }
      List<? extends List<? extends CharSequence>> res2 = list;
    System.out.println(res2);
  }

  static class MyList<T> extends ArrayList<List<? extends CharSequence>> {
  }

  private void collectCustomList() {
      MyList<? extends List<? extends CharSequence>> res2 =
              new MyList<>();
      for (CharSequence charSequence: getListExtends()) {
          List<? extends CharSequence> charSequences = asList(charSequence);
          res2.add(charSequences);
      }
      System.out.println(res2);
  }

  static class MyList2<T, X> extends ArrayList<X> {
  }

  public MyList2<? extends Number, CharSequence> createMyList2() {
    return new MyList2<>();
  }

  private void collectGroupingByCustomList() {
      Map<Integer, MyList2<? extends Number, CharSequence>> result = new HashMap<>();
      for (CharSequence x: getList()) {
          result.computeIfAbsent(x.length(), k -> createMyList2()).add(x);
      }
      Map<Integer, ? extends MyList2<? extends Number, ? extends CharSequence>> map =
              result;
    System.out.println(map);
  }

  private void collectToMap() {
      Map<CharSequence, List<? extends CharSequence>> result = new HashMap<>();
      for (CharSequence charSequence: getList()) {
          if (charSequence != null) {
              if (result.put(charSequence, asList(charSequence)) != null) {
                  throw new IllegalStateException("Duplicate key");
              }
          }
      }
      Map<? extends CharSequence, ? extends List<? extends CharSequence>> map = result;
    System.out.println(map);
  }
}
