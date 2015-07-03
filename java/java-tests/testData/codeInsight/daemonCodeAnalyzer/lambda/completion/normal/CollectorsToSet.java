import java.util.*;

class Foo {
  void m() {
    Iterable<CharSequence> l = Arrays.asList("a", "b").stream().colle<caret>
  }
}