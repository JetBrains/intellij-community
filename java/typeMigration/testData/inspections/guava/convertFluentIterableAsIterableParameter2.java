import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;

import java.util.ArrayList;

public class Main16 {
  void m() {
    //read-only method with Iterable<A> parameter
    Iterables.cycle(FluentIterable.fro<caret>m(new ArrayList<String>()).transform(s -> s));
  }
}
