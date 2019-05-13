import com.google.common.collect.FluentIterable;


public class B {
  public <U> U[] toParamArray(FluentIterable<U> <caret>p, Class<U> c) { return p.toArray(c); }
}
