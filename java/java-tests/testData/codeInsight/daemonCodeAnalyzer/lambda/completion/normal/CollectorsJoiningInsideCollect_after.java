import java.util.*;
import java.util.stream.Collectors;

class Foo {
  void m() {
    String l = Arrays.asList("a", "b").stream().collect(Collectors.joining(<caret>))
  }
}