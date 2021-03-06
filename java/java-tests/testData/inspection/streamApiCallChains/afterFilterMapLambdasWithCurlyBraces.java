// "Swap 'filter()' and 'map()'" "true"

import java.util.List;

class X {
  void foo(List<String> list) {
    list.stream().map(x -> {
      return x.toUpperCase();
    }).filter(s -> {
      return s.length() > 3;
    }).count();
  }
}
