import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class X {

  void foo() {
      List<String> <caret>list = new ArrayList<>();
      list.add("foo");
      process(list);
  }

  void process(List<String> list) {
  }
}