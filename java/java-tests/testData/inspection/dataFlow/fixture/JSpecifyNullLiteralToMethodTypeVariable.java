import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class NullLiteralToMethodTypeVariable {

  <ParametricT extends @Nullable Object> ParametricT returnsNull() {
    return <warning descr="'null' is returned from a method whose type-variable return type may be instantiated as non-null">null</warning>;
  }

  <ParametricT extends @Nullable Object> @Nullable ParametricT returnsNullAnnotated() {
    return null;
  }

  <ParametricT extends @Nullable Object> ParametricT passthrough(ParametricT value) {
    return value;
  }
}
