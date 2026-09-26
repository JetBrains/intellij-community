import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@NullMarked
class NewExpressionBoundTransitive {
  interface Wrap<T extends @Nullable Object> { }

  interface Foo { }

  // H < G < F
  static class Holder3<F extends @Nullable Object, G extends F, H extends G> { }

  void run() {
    // transitive pair H<F is a mismatch
    new <warning descr="Incompatible type arguments due to nullability">Holder3<Wrap<? extends Foo>, Wrap<? extends @NullnessUnspecified Foo>, Wrap<? extends @Nullable Foo>></warning>();
  }
}
