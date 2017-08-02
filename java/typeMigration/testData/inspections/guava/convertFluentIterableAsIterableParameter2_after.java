import com.google.common.collect.Iterables;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class Main16 {
  void m() {
    //read-only method with Iterable<A> parameter
    Iterables.cycle(new ArrayList<String>().stream().map(s -> s).collect(Collectors.toList()));
  }
}
