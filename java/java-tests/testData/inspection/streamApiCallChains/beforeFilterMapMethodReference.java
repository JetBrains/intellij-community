// "Swap 'filter()' and 'map()'" "true"

import java.util.List;

class X {
  void foo(List<String> list) {
    list.stream().filter(x -> x.toUpperCase().length() > 3).ma<caret>p(String::toUpperCase).count();
  }
}
