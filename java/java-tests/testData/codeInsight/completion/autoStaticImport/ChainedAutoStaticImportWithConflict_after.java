import java.util.*;
import java.util.stream.Collectors;

import static org.example.AAA.*;

class Foo {
  void m() {
    List<CharSequence> l = Arrays.asList("a", "b").stream().collect(Collectors.toList());<caret>
  }
}