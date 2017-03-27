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
    List<? extends CharSequence> res = getListExtends().stream().filter(Objects::nonNull).co<caret>llect(Collectors.toList());
    System.out.println(res);
  }

  private void collect2() {
    List<? extends List<? extends CharSequence>> res2 = getListExtends().stream().map(this::asList).collect(Collectors.toList());
    System.out.println(res2);
  }

  static class MyList<T> extends ArrayList<List<? extends CharSequence>> {
  }

  private void collectCustomList() {
    MyList<? extends List<? extends CharSequence>> res2 =
      getListExtends().stream().map(this::asList).collect(Collectors.toCollection(MyList::new));
    System.out.println(res2);
  }

  static class MyList2<T, X> extends ArrayList<X> {
  }

  public MyList2<? extends Number, CharSequence> createMyList2() {
    return new MyList2<>();
  }

  private void collectGroupingByCustomList() {
    Map<Integer, ? extends MyList2<? extends Number, ? extends CharSequence>> map =
      getList().stream().collect(Collectors.groupingBy(x -> x.length(), Collectors.toCollection(this::createMyList2)));
    System.out.println(map);
  }

  private void collectToMap() {
    Map<? extends CharSequence, ? extends List<? extends CharSequence>> map = getList()
      .stream().filter(Objects::nonNull).collect(Collectors.toMap(Function.identity(), this::asList));
    System.out.println(map);
  }
}
