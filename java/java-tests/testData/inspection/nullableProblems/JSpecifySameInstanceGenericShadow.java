import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@NullMarked
class JSpecifySameInstanceGenericShadow<E extends @Nullable Object> {
  interface Box<X extends @Nullable Object> {}

  // the method type parameter E shadows the class type parameter E; the method-level E has a not-null bound
  <E> void acceptUnspec(Box<@NullnessUnspecified E> box) {}

  void run(Box<@Nullable E> nullableBox) {
    acceptUnspec(<warning descr="Assigning a class with nullable type arguments when a class with not-null type arguments is expected">nullableBox</warning>);
  }
}
