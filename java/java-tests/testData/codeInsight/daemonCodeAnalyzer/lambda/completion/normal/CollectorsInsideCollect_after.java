import java.util.*;
import java.util.stream.Collectors;

class Foo {
  void m() {
    Iterable<CharSequence> l = Arrays.asList("a", "b").stream().collect(Collectors.toList())
  }
}