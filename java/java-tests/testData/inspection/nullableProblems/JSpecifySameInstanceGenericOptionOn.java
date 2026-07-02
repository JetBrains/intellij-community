import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@NullMarked
class JSpecifySameInstanceGenericOptionOn {
  interface Box<E extends @Nullable Object> {}

  interface Source<V extends @Nullable Object> {
    Box<V> create();

    Box<@NullnessUnspecified V> createUnspec();

    void acceptNonNull(Box<? extends Object> box);

    void acceptNullable(Box<@Nullable V> box);

    default void use() {
      // NOT_NULL expected, unspecified actual that hides a @Nullable bound: reported only with the option on
      acceptNonNull(<warning descr="Assigning a class with nullable type arguments when a class with not-null type arguments is expected">createUnspec()</warning>);

      // both nullable, but one nullability comes from the bound: always reported (COMPLEX)
      acceptNullable(<warning descr="Incompatible type arguments due to nullability">create()</warning>);
    }
  }
}
