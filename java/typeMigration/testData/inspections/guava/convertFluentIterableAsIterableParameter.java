import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;

import java.util.ArrayList;

public class Main16 {
  void m() {
    FluentIterable<String> i<caret>t = FluentIterable.from(new ArrayList<String>()).transform(s -> s);

    //read-only method with Iterable<A> parameter
    Iterables.cycle(it);
  }
}
