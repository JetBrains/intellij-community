import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;

class Bug04 {

  private static final List<X> list = new ArrayList<>();

  private static List<? extends CharSequence> list() {
    return list.stream().map(x -> x.getList().get(0)).collect(toList());
  }

  public static void main(String... args) {
    System.out.println(list());
  }

  private static class X {

    private final List<? extends CharSequence> list = Collections.singletonList("x");

    public List<? extends CharSequence> getList() {
      return list;
    }
  }
}