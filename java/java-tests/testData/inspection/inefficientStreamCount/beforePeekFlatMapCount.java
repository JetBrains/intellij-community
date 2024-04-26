// "Replace with 'Stream.mapToLong().sum()'" "true-preview"

import java.util.Collection;
import java.util.List;

class Test {
  void foo(List<List<String>> s) {
    long count = s.stream().peek(System.out::println).flatMap(Collection::stream).c<caret>ount();
  }
}