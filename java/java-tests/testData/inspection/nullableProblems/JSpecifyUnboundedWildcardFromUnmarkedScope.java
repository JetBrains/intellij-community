import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

class JSpecifyUnboundedWildcardFromUnmarkedScope {
  @NullMarked
  interface Lib<T extends @Nullable Object> {}

  interface Unmarked {
    Lib<?> get();
  }

  @NullMarked
  interface Marked {
    Lib<?> get();
  }

  @NullMarked
  static class Caller {
    Lib<? extends Object> fromUnmarked(Unmarked u) {
      // the '?' is written in unmarked code, so nothing says the type argument may be null
      return u.get();
    }

    Lib<? extends Object> fromMarked(Marked m) {
      // the '?' is written in a @NullMarked scope, where its implicit bound is '@Nullable Object'
      return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected">m.get()</warning>;
    }
  }
}
