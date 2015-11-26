import com.google.common.collect.FluentIterable;


public class B {
  public <U> U[] toParamArray(FluentIte<caret>rable<U> p, Class<U> c) { return p.toArray(c); }
}
