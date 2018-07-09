import java.util.*;
import java.util.stream.Collectors;

class Foo {
  void m() {
    Arrays.asList(1, 2).stream().collect(Collectors.toCollection(<caret>))
  }
}