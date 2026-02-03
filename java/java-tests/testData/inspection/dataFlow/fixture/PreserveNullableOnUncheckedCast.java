import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class BrokenAlignment {
  void t() {
    @NotNull Collection list = new ArrayList();
    List<String> strings = (List<String>) list;
    if (<warning descr="Condition 'strings != null' is always 'true'">strings != null</warning>) {
      int foo = 42;
    }
  }

}