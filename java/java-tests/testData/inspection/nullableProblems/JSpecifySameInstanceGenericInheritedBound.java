import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class JSpecifySameInstanceGenericInheritedBound {
  interface Tag {}

  interface Box<E extends @Nullable Object> {}

  interface Source<V extends @Nullable Object> {
    Box<V> create();

    void acceptNullable(Box<@Nullable V> box);
  }

  interface Derived<V extends @Nullable Object & @Nullable Tag> extends Source<V> {
    default void use() {
      acceptNullable(<warning descr="Incompatible type arguments due to nullability">create()</warning>);
    }
  }
}
