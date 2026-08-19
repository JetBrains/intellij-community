import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@NullMarked
class JSpecifyTypeParameterBoundOverridesNullable {
  interface Sequence<T extends @Nullable Object> {}

  interface Filter<T extends @Nullable Object> {
    <U extends T> Sequence<U> filterMany(Sequence<U> in);

    Sequence<T> filterAll();
  }

  interface SubImplicitBound extends Filter<@Nullable Object> {
    // the implicit bound is `extends Object`, which is not-null in a @NullMarked scope
    <<warning descr="Type parameter 'U' must not narrow the nullable bound declared by the overridden method in 'Filter'">U</warning>> Sequence<U> filterMany(Sequence<U> in);
  }

  interface SubNotNullBound extends Filter<@Nullable Object> {
    <<warning descr="Type parameter 'U' must not narrow the nullable bound declared by the overridden method in 'Filter'">U</warning> extends Object> Sequence<U> filterMany(Sequence<U> in);
  }

  interface SubNullableBound extends Filter<@Nullable Object> {
    <U extends @Nullable Object> Sequence<U> filterMany(Sequence<U> in);
  }

  interface SubNotNullTypeArgument extends Filter<Object> {
    // the supertype itself only allows not-null arguments, so narrowing is not a mismatch
    <U> Sequence<U> filterMany(Sequence<U> in);

    // the inherited `Sequence<T>` is `Sequence<Object>` here, so a not-null argument is expected as well
    Sequence<Object> filterAll();
  }

  interface SubNullableTypeArgument extends Filter<@Nullable Object> {
    // the inherited `Sequence<T>` is `Sequence<@Nullable Object>` here, so the not-null argument is a real mismatch
    <warning descr="Overriding a class with not-null type arguments when a class with nullable type arguments is expected">Sequence<Object></warning> filterAll();
  }
}
