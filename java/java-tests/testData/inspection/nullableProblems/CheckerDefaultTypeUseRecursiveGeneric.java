
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

import java.util.ArrayList;

@DefaultQualifier(NonNull.class)
final class Experiment {
  private static abstract class Base<R extends Base<R>> {
    public abstract R self();
  }
  private static final class Impl extends Base<Impl> {
    @Override
    public Impl self() {
      return this;
    }
  }
  public static void main(String[] arguments) {
    Base<?> Base = new Impl();
  }
}