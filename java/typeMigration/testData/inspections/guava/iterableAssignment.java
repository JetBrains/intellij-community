import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class A {
  void m() {
    Iterable<String> it = Fl<caret>uentIterable.from(new ArrayList<String>()).transform(s -> s + s);
  }
}