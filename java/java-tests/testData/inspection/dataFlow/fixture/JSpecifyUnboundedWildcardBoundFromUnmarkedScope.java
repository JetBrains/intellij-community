import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

class JSpecifyUnboundedWildcardBoundFromUnmarkedScope {
  @NullMarked
  interface Box<T extends Object> {
    T get();
  }

  @NullMarked
  interface Lib<T extends @Nullable Object> {
    T get();
  }

  interface Unmarked {
    Box<?> box();

    Lib<?> lib();
  }

  @NullMarked
  interface Marked {
    Lib<?> lib();
  }

  @NullMarked
  static class Caller {
    void notNullBound(Unmarked u) {
      if (<warning descr="Condition 'u.box().get() == null' is always 'false'">u.box().get() == null</warning>) return;
    }

    void nullableBoundFromUnmarkedScope(Unmarked u) {
      if (u.lib().get() == null) return;
    }

    void nullableBoundFromMarkedScope(Marked m) {
      m.lib().get().<warning descr="Method invocation 'toString' may produce 'NullPointerException'">toString</warning>();
    }
  }
}
