import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class Main21 {
  void m() {
    FluentIterable<String> i<caret>t = FluentIterable.from(new ArrayList<String>());

    boolean s = it.contains("asd");

  }
}
