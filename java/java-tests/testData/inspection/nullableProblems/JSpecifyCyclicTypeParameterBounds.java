import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

class JSpecifyCyclicTypeParameterBounds {
  @NullMarked
  interface Cyclic<<error descr="Cyclic inheritance involving 'A'"></error>A extends @Nullable B, B extends @Nullable A> {}

  interface Unmarked {
    Cyclic<?, ?> get();
  }

  @NullMarked
  static class Caller {
    Object use(Unmarked u) {
      Cyclic<?, ?> c = u.get();
      return c;
    }
  }
}
