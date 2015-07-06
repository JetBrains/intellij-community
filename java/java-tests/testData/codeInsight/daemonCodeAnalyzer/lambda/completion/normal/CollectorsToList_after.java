import java.util.*;
import java.util.stream.Collectors;

class Foo {
  void m() {
    List<CharSequence> l = Arrays.asList("a", "b").stream().collect(Collectors.toList());<caret>
  }
}