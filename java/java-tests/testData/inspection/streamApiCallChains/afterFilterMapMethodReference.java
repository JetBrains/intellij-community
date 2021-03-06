// "Swap 'filter()' and 'map()'" "true"

import java.util.List;

class X {
  void foo(List<String> list) {
    list.stream().map(String::toUpperCase).filter(s -> s.length() > 3).count();
  }
}
