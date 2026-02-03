import com.google.common.collect.FluentIterable;

import java.util.ArrayList;

public class MainFluentIterable {

  FluentIter<caret>able<String> m2() {

    FluentIterable<String> it = FluentIterable.from(new ArrayList<String>());

    return it.transform(s -> s + s);
  }

  void m3() {
    System.out.println((int) m2().size());
  }
}