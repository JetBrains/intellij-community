import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;

public class X {

  void m(Fluen<caret>tIterable<String> it, Function<String, String> f) {
    ImmutableMap<String, String> index = it.uniqueIndex(f);
    System.out.println(index.get("asd"));
  }
}