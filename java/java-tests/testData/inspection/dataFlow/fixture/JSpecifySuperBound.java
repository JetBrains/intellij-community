import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class SuperTypeVariable {
  <T extends @Nullable Object> void nullableBounded(
    Lib<? super T> lib, T t, @Nullable T tUnionNull) {
    lib.useT(<warning descr="Argument 'tUnionNull' might be null">tUnionNull</warning>);

    lib.useT(t);
  }
  <T> void implicitlyObjectBounded(
    Lib<? super T> lib, T t, @Nullable T tUnionNull) {
    lib.useT(<warning descr="Argument 'tUnionNull' might be null">tUnionNull</warning>);

    lib.useT(t);
  }
  <T> void noSuper(
    Lib<T> lib, T t, @Nullable T tUnionNull) {
    lib.useT(<warning descr="Argument 'tUnionNull' might be null">tUnionNull</warning>);

    lib.useT(t);
  }
  interface Lib<T extends @Nullable Object> {
    void useT(T t);
  }
}