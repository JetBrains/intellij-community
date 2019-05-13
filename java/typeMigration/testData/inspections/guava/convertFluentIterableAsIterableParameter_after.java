import com.google.common.collect.Iterables;

import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main16 {
  void m() {
    Stream<String> it = new ArrayList<String>().stream().map(s -> s);

    //read-only method with Iterable<A> parameter
    Iterables.cycle(it.collect(Collectors.toList()));
  }
}
