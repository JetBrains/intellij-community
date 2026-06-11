import java.util.Collections;
import java.util.List;

class X {

  void foo() {
    process(Collections.<caret>singletonList("foo"));
  }

  void process(List<String> list) {
  }
}