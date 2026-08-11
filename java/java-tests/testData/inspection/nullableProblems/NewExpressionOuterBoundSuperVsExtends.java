import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class NewExpressionOuterBoundSuperVsExtends<F extends @Nullable Object> {
  interface Wrap<T extends @Nullable Object> { }

  interface Elem { }

  class Holder<A extends F> { }

  void mismatches() {
    new NewExpressionOuterBoundSuperVsExtends<Wrap<? extends Object>>().new <warning descr="Incompatible type arguments due to nullability">Holder<Wrap<? super Object>></warning>();
  }

  void wellFormed() {
    new NewExpressionOuterBoundSuperVsExtends<Wrap<? extends @Nullable Object>>().new Holder<Wrap<? super Object>>();
  }
}
