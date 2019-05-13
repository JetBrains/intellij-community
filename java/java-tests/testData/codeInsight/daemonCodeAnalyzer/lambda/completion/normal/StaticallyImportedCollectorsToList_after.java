import java.util.*;
import static java.util.stream.Collectors.*;

class Foo {
  void m() {
    List<CharSequence> l = Arrays.asList("a", "b").stream().collect(toList());<caret>
  }
}