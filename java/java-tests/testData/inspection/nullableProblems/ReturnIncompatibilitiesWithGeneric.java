import org.jspecify.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Collection;

class B<T> {
  B<@NotNull String> simpleNullableToNotNull(B<@Nullable String> arg) {
    return <warning descr="Returning a class with nullable type parameters when a class with non-null type parameters is expected">arg</warning>;
  }

  B<@Nullable String> simpleNotNullToNullable(B<@NotNull String> arg) {
    return <warning descr="Returning a class with non-null type parameters when a class with nullable type parameters is expected">arg</warning>;
  }

  B<B<@NotNull String>> nested(B<B<@Nullable String>> arg) {
    return <warning descr="Returning a class with nullable type parameters when a class with non-null type parameters is expected">arg</warning>;
  }

  B<? extends @Nullable Object> extendsWildcardNullable(B<@NotNull Object> arg) {
    return arg;
  }

  B<? extends @NotNull Object> extendsWildcardNotNull(B<@Nullable String> arg) {
    return <warning descr="Returning a class with nullable type parameters when a class with non-null type parameters is expected">arg</warning>;
  }

  B<? super @NotNull String> SupperWildcard(B<@Nullable Object> arg) {
    return arg;
  }

  B<? extends @NotNull Object> extendsWildcardBothNotNull(B<? extends @Nullable Object> arg) {
    return <warning descr="Returning a class with nullable type parameters when a class with non-null type parameters is expected">arg</warning>;
  }

  B<? extends @Nullable Object> extendsWildcardBothNullable(B<? extends @NotNull Object> arg) {
    return arg;
  }

  B<? super @NotNull Object> superWildCardBoth(B<? super @Nullable Object> arg) {
    return arg;
  }

  B<? extends B<@NotNull Object>> nestedWithWildcard(B<B<@Nullable Object>> arg) {
    return <warning descr="Returning a class with nullable type parameters when a class with non-null type parameters is expected">arg</warning>;
  }

  Map<List<String>, List<@Nullable String>> checkIsPerformedIfSecondTypeArgumentIsTheSame(Map<List<String>, List<@NotNull String>> arg) {
    return <warning descr="Returning a class with non-null type parameters when a class with nullable type parameters is expected">arg</warning>;
  }

  B<@NotNull String>[] array(B<@Nullable String>[] arg) {
    return <warning descr="Returning a class with nullable type parameters when a class with non-null type parameters is expected">arg</warning>;
  }

  Object[] @NotNull [] nullabilityInNestedArray(Object[] @Nullable [] arg) {
    return <warning descr="Returning a class with nullable type parameters when a class with non-null type parameters is expected">arg</warning>;
  }

  B<@NotNull String>[][] multiDimensionalArray(B<@Nullable String>[][] arg) {
    return <warning descr="Returning a class with nullable type parameters when a class with non-null type parameters is expected">arg</warning>;
  }

  static class C<T, V> {
    C<Object, @NotNull String> secondArgument(C<Object, @Nullable String> arg) {
      return <warning descr="Returning a class with nullable type parameters when a class with non-null type parameters is expected">arg</warning>;
    }
  }

  interface I {}

  List<@Nullable String> intersectionSimple(Object arg) {
    return <warning descr="Returning a class with non-null type parameters when a class with nullable type parameters is expected">(List<@NotNull String> & I) arg</warning>;
  }

  Collection<@Nullable String> intersectionTreatsFirstMatchingUnknown(Object arg) {
    return (Collection<String> & List<@NotNull String> & I) arg;
  }

  Collection<@Nullable String> intersectionTreatsFirstMatchingMismatch(Object arg) {
    return <warning descr="Returning a class with non-null type parameters when a class with nullable type parameters is expected">(Collection<@NotNull String> & List<@NotNull String> & I) arg</warning>;
  }

  List<List<@Nullable String>> nestedIntersection(Object arg) {
    return <warning descr="Returning a class with non-null type parameters when a class with nullable type parameters is expected">(List<List<@NotNull String>> & I) arg</warning>;
  }
}