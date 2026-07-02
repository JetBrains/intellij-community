import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@NullMarked
class JSpecifySameInstanceGenericOptionOff {
  interface Box<E extends @Nullable Object> {}

  interface Source<V extends @Nullable Object> {
    Box<V> create();

    Box<@NullnessUnspecified V> createUnspec();

    void acceptNonNull(Box<? extends Object> box);

    void acceptNullable(Box<@Nullable V> box);

    default void use() {
      // unspecified actual: NOT reported when the option is off
      acceptNonNull(createUnspec());

      // COMPLEX conflict is not gated by the option: still reported
      acceptNullable(<warning descr="Incompatible type arguments due to nullability">create()</warning>);
    }
  }
}
