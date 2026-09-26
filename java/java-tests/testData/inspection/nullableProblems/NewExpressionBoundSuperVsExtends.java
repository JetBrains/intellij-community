import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class NewExpressionBoundSuperVsExtends {
  interface Wrap<T extends @Nullable Object> { }

  interface Elem { }

  static class Holder<F extends @Nullable Object, A extends F> { }

  void mismatches() {
    new <warning descr="Incompatible type arguments due to nullability">Holder<Wrap<? extends Object>, Wrap<? super Object>></warning>();
  }

  void wellFormed() {
    new Holder<Wrap<? extends @Nullable Object>, Wrap<? super Object>>();
  }
}
