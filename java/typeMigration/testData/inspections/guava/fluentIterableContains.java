import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class Main21 {
  void m() {
    FluentIterable<String> it = FluentIterable.fr<caret>om(new ArrayList<String>());

    boolean s = it.contains("asd");

  }
}
