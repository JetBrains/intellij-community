// "Replace with 'Objects.equals'" "true"
import java.util.*;

class Test {
  void test() {
    final boolean ff = Collections.singleton("foo").contai<caret>ns("bar");
  }
}