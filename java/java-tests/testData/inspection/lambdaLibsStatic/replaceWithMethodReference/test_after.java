import com.google.common.collect.Iterables;

import java.lang.String;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

class c {
  void m() {
    List<String> l = new ArrayList<>();
    Iterable<Boolean> transform = l.stream().map(String::isEmpty).collect(Collectors.toList());
  }
}