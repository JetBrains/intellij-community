// "Swap 'filter()' and 'map()'" "true-preview"

import java.util.List;

class X {
  void foo(List<String> list) {
    list.stream().map((((x) -> {
      return ((x.toUpperCase()));
    }))).filter(((upperCase -> ((((((upperCase)).length())) > 3))))).count();
  }
}
