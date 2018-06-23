// "Remove type arguments" "false"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

class Test {
  void foo(List<String> input, Function<String, Integer> length) {
    input.stream().collect(Collectors.groupingBy(length,
                                                 Collectors.collectingAndThen(Collectors.<St<caret>ring>maxBy(Comparator.naturalOrder()), Optional::get)));
  }
}