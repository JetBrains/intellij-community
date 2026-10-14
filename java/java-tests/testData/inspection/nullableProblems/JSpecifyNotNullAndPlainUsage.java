import org.jspecify.annotations.NotNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class JSpecifyNotNullAndPlainUsage {
  interface Pair<L extends @Nullable Object, R extends @Nullable Object> {
    R right();
  }

  static <A extends @Nullable Object> Pair<@NotNull A, A> pair(A a) { throw new UnsupportedOperationException(); }

  static <B extends @Nullable Object> B right(Pair<? extends B, ? extends B> p) { return p.right(); }

  @Nullable String test(@Nullable String s) {
    return right(pair(s));
  }
}