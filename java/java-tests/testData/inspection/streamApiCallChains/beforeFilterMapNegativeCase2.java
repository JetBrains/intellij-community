// "Swap 'filter()' and 'map()'" "false"

import java.util.List;

class X {
  void foo(List<String> list) {
    list.stream().filter(x -> x != null && x.toUpperCase().length() > 3).ma<caret>p(x -> x.toUpperCase()).count();
  }
}
