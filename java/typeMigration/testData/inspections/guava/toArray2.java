import com.google.common.collect.FluentIterable;

import java.util.List;

public class B {
  public List<String>[] toCollectionArray(Fluen<caret>tIterable<List<String>> p, Class<List<String>> c) { return p.toArray(c); }
}
