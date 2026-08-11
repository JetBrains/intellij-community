import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class NewExpressionBoundContainment {
  interface Wrap<T extends @Nullable Object> { }

  interface Elem { }

  static class Holder<F extends @Nullable Object, A extends F> { }

  void extendsContainment() {
    new Holder<Wrap<? extends Elem>, Wrap<? extends Elem>>();
    new Holder<Wrap<? extends @Nullable Elem>, Wrap<? extends Elem>>();
    new Holder<Wrap<? extends @Nullable Elem>, Wrap<? extends @Nullable Elem>>();
  }

  void superContainment() {
    new Holder<Wrap<? super Elem>, Wrap<? super Elem>>();
    new Holder<Wrap<? super Elem>, Wrap<? super @Nullable Elem>>();
    new Holder<Wrap<? super @Nullable Elem>, Wrap<? super @Nullable Elem>>();

    new <warning descr="Incompatible type arguments due to nullability">Holder<Wrap<? super @Nullable Elem>, Wrap<? super Elem>></warning>();
  }
}
