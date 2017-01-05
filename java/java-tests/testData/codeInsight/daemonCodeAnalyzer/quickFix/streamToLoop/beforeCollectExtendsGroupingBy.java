// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    Map<Integer, ? extends MyList<? extends Number, ? extends CharSequence>> map =
      getList().stream().co<caret>llect(Collectors.groupingBy(x -> x.length(), Collectors.toCollection(this::createList)));
    System.out.println(map);
  }
