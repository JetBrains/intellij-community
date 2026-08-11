import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class JSpecifySameInstanceGenericSimple<E extends @Nullable Object> {
  Box<E> identity(Box<E> value) {
    return value;
  }

  Box<E> narrow(Box<@Nullable E> value) {
    return <warning descr="Incompatible type arguments due to nullability">value</warning>;
  }

  Box<@Nullable E> widen(Box<E> value) {
    return <warning descr="Incompatible type arguments due to nullability">value</warning>;
  }

  interface Box<V extends @Nullable Object> {}
}
