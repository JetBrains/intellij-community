import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class NewExpressionBoundExtends {
  interface Wrap<T extends @Nullable Object> { }

  interface Elem { }

  static class Holder<F extends @Nullable Object, A extends F> { }

  void run() {
    new <warning descr="Incompatible type arguments due to nullability">Holder<Wrap<? extends Elem>, Wrap<? extends @Nullable Elem>></warning>();
  }
}
