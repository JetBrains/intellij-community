import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class ReferenceBoundPositions {
  interface Wrap<T extends @Nullable Object> { }

  interface Elem { }

  static class Holder<F extends @Nullable Object, A extends F> { }

  <warning descr="Incompatible type arguments due to nullability">Holder<Wrap<? extends Elem>, Wrap<? extends @Nullable Elem>></warning> field;

  Holder<Wrap<? extends @Nullable Elem>, Wrap<? extends @Nullable Elem>> ok;
}
