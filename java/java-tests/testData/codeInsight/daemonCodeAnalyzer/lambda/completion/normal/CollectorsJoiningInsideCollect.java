import java.util.*;

class Foo {
  void m() {
    String l = Arrays.asList("a", "b").stream().collect(joi<caret>)
  }
}