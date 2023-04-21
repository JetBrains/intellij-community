// "Swap 'filter()' and 'map()'" "true-preview"

import java.util.List;

class X {
  void foo(List<String> list) {
    list.stream().map(x -> {
      return x.toUpperCase();
    }).filter(upperCase -> {
      return upperCase.length() > 3;
    }).count();
  }
}
