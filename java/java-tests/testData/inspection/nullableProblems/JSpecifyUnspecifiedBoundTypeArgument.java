import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@NullMarked
class JSpecifyUnspecifiedBoundTypeArgument {
  interface Box<E extends @Nullable Object> {}

  interface Source<P extends @Nullable Object, V extends @NullnessUnspecified P> {
    Box<V> create();

    void acceptNonNull(Box<? extends Object> box);

    default void use() {
      acceptNonNull(<warning descr="Assigning a class with nullable type arguments when a class with not-null type arguments is expected">create()</warning>);
    }
  }
}
