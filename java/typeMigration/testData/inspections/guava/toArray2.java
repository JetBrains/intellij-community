import com.google.common.collect.FluentIterable;

import java.util.List;

public class B {
  public List<String>[] toCollectionArray(FluentIterable<List<String>> <caret>p, Class<List<String>> c) { return p.toArray(c); }
}
