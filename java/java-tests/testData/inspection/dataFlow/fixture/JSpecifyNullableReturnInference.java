import org.assertj.core.api.Assertions;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class JSpecifyNullableReturnInference {
  static class Base {
  }

  private static <T extends @Nullable Base> @Nullable T getNullable(Class<T> ignore) {
    return null;
  }

  private static <T extends @Nullable Base> T getParametric(Class<T> ignore) {
    return <warning descr="'null' is returned from a method whose type-variable return type may be instantiated as non-null">null</warning>;
  }

  static void explicitVariable() {
    var x = getNullable(Base.class);
    Assertions.assertThat(x).isNull();
  }

  static void explicitTypeArgument() {
    Assertions.assertThat(JSpecifyNullableReturnInference.<@Nullable Base>getNullable(Base.class)).isNull();
  }

  // The type argument of assertThat is inferred from the @Nullable return type, so it stays nullable
  static void inferredTypeArgument() {
    Assertions.assertThat(getNullable(Base.class)).isNull();
  }

  static void inferredTypeArgumentInCondition() {
    if (getNullable(Base.class) == null) {}
  }

  // The @Nullable bound alone does not make the return type nullable. T is instantiated with the non-null Base,
  // so the call returns a non-null value. The unsound `return null` in getParametric is reported instead.
  static void parametricReturnStaysNotNull() {
    Assertions.assertThat(getParametric(Base.class)).<warning descr="The call to 'isNull' always fails with an exception">isNull</warning>();
  }

  static void parametricReturnStaysNotNullInCondition() {
    if (<warning descr="Condition 'getParametric(Base.class) == null' is always 'false'">getParametric(Base.class) == null</warning>) {}
  }
}
