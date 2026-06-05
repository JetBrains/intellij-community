import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class NullLiteralToTypeVariable<ParametricT extends @Nullable Object, ParametricT2> {

  ParametricT returnsNull() {
    return <warning descr="'null' is returned from a method whose type-variable return type may be instantiated as non-null">null</warning>;
  }

  @Nullable ParametricT returnsNullAnnotated() {
    return null;
  }

  ParametricT passthrough(ParametricT value) {
    return value;
  }
}
