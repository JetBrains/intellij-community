import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class JSpecifyParametricNullableField<T extends @Nullable Object> {

  T inInitializer = <warning descr="'null' is assigned to a variable whose type-variable type may be instantiated as non-null">null</warning>;

  @Nullable T nullableField = null;

  T inConstructor;

  JSpecifyParametricNullableField() {
    inConstructor = <warning descr="'null' is assigned to a variable whose type-variable type may be instantiated as non-null">null</warning>;
  }
}
