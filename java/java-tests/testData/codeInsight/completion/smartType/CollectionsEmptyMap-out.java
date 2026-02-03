import java.util.Collections;
import java.util.Map;

class Foo {
  void m() {
    Map<String, Integer> m = Collections.emptyMap();<caret>
  }

}