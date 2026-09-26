// "Swap 'filter()' and 'map()'" "false"

import java.util.List;

class X {
  void foo(List<String> list) {
    list.stream().filter(x -> x.toUpperCase().length() > 3).ma<caret>p(x -> x.toUpperCase().toLowerCase()).count();
  }
}
