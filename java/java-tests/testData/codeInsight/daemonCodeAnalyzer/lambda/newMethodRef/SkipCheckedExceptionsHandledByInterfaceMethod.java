import java.io.IOException;

class MethodReferenceErrorHighlight  {
  {
    match(String.class, this::foo);
  }

  <P> void match(final Class<P> type, UnitApply<P> apply) {}
  void foo(String s) throws IOException { throw new IOException(); }
}

interface UnitApply<I> {
  void apply(I i) throws Exception;
}