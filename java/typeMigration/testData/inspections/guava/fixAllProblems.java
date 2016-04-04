import com.google.common.base.Optional;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;

import java.util.ArrayList;
import java.util.List;


public class Collector {
  public int create111() {
    return FluentIterable.from(new ArrayList<String>()).transform(s -> s + s).size();
  }

  public FluentIterable<String> create() {
    return FluentIterable.from(new ArrayList<String>()).transform(s -> s + s);
  }

  public FluentIterable<String> create2() {
    return create().limit(12);
  }

  public FluentIterable<String> create3() {
    return create2().limit(12);
  }

  public Optional<String> m(FluentIterable<String> fi, Function<String, String> f1, Function<String, String> f2) {
    return fi.transform(f1).transform(f2).first();
  }
}