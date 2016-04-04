import java.util.stream.Stream;


public class B {
  public <U> U[] toParamArray(Stream<U> p, Class<U> c) { return p.toArray(i -> (U[]) new Object[i]); }
}
