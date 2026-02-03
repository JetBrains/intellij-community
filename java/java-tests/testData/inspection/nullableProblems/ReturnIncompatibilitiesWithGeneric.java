import org.jspecify.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import java.util.List;
import java.util.Map;
import java.util.Collection;

class B<T> {
  B<@NotNull String> simpleNullableToNotNull(B<@Nullable String> arg) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>B<<b>@NotNull</b> String></td></tr><tr><td>Actual type:</td><td>B<<b>@Nullable</b> String></td></tr></table>">arg</warning>;
  }

  B<@Nullable String> simpleNotNullToNullable(B<@NotNull String> arg) {
    return <warning descr="Returning a class with not-null type arguments when a class with nullable type arguments is expected" tooltip="Returning a class with not-null type arguments when a class with nullable type arguments is expected<table><tr><td>Expected type:</td><td>B<<b>@Nullable</b> String></td></tr><tr><td>Actual type:</td><td>B<<b>@NotNull</b> String></td></tr></table>">arg</warning>;
  }

  B<B<@NotNull String>> nested(B<B<@Nullable String>> arg) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>B<B<<b>@NotNull</b> String>></td></tr><tr><td>Actual type:</td><td>B<B<<b>@Nullable</b> String>></td></tr></table>">arg</warning>;
  }

  B<? extends @Nullable Object> extendsWildcardNullable(B<@NotNull Object> arg) {
    return arg;
  }

  B<? extends @NotNull Object> extendsWildcardNotNull(B<@Nullable String> arg) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>B<? extends <b>@NotNull</b> Object></td></tr><tr><td>Actual type:</td><td>B<<b>@Nullable</b> String></td></tr></table>">arg</warning>;
  }

  B<? super @NotNull String> SupperWildcard(B<@Nullable Object> arg) {
    return arg;
  }

  B<? extends @NotNull Object> extendsWildcardBothNotNull(B<? extends @Nullable Object> arg) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>B<? extends <b>@NotNull</b> Object></td></tr><tr><td>Actual type:</td><td>B<? extends <b>@Nullable</b> Object></td></tr></table>">arg</warning>;
  }

  B<? extends @Nullable Object> extendsWildcardBothNullable(B<? extends @NotNull Object> arg) {
    return arg;
  }

  B<? super @NotNull Object> superWildCardBoth(B<? super @Nullable Object> arg) {
    return arg;
  }

  B<? extends B<@NotNull Object>> nestedWithWildcard(B<B<@Nullable Object>> arg) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>B<? extends B<<b>@NotNull</b> Object>></td></tr><tr><td>Actual type:</td><td>B<B<<b>@Nullable</b> Object>></td></tr></table>">arg</warning>;
  }

  Map<List<String>, List<@Nullable String>> checkIsPerformedIfSecondTypeArgumentIsTheSame(Map<List<String>, List<@NotNull String>> arg) {
    return <warning descr="Returning a class with not-null type arguments when a class with nullable type arguments is expected" tooltip="Returning a class with not-null type arguments when a class with nullable type arguments is expected<table><tr><td>Expected type:</td><td>Map<List<String>, List<<b>@Nullable</b> String>></td></tr><tr><td>Actual type:</td><td>Map<List<String>, List<<b>@NotNull</b> String>></td></tr></table>">arg</warning>;
  }

  B<@NotNull String>[] array(B<@Nullable String>[] arg) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>B<<b>@NotNull</b> String>[]</td></tr><tr><td>Actual type:</td><td>B<<b>@Nullable</b> String>[]</td></tr></table>">arg</warning>;
  }

  Object[] @NotNull [] nullabilityInNestedArray(Object[] @Nullable [] arg) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>Object[] <b>@NotNull</b> []</td></tr><tr><td>Actual type:</td><td>Object[] <b>@Nullable</b> []</td></tr></table>">arg</warning>;
  }

  B<@NotNull String>[][] multiDimensionalArray(B<@Nullable String>[][] arg) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>B<<b>@NotNull</b> String>[][]</td></tr><tr><td>Actual type:</td><td>B<<b>@Nullable</b> String>[][]</td></tr></table>">arg</warning>;
  }

  static class C<T, V> {
    C<Object, @NotNull String> secondArgument(C<Object, @Nullable String> arg) {
      return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>C<Object, <b>@NotNull</b> String></td></tr><tr><td>Actual type:</td><td>C<Object, <b>@Nullable</b> String></td></tr></table>">arg</warning>;
    }
  }

  interface I {}

  List<@Nullable String> intersectionSimple(Object arg) {
    return <warning descr="Returning a class with not-null type arguments when a class with nullable type arguments is expected" tooltip="Returning a class with not-null type arguments when a class with nullable type arguments is expected">(List<@NotNull String> & I) arg</warning>;
  }

  Collection<@Nullable String> intersectionTreatsFirstMatchingUnknown(Object arg) {
    return (Collection<String> & List<@NotNull String> & I) arg;
  }

  Collection<@Nullable String> intersectionTreatsFirstMatchingMismatch(Object arg) {
    return <warning descr="Returning a class with not-null type arguments when a class with nullable type arguments is expected" tooltip="Returning a class with not-null type arguments when a class with nullable type arguments is expected">(Collection<@NotNull String> & List<@NotNull String> & I) arg</warning>;
  }

  List<List<@Nullable String>> nestedIntersection(Object arg) {
    return <warning descr="Returning a class with not-null type arguments when a class with nullable type arguments is expected" tooltip="Returning a class with not-null type arguments when a class with nullable type arguments is expected">(List<List<@NotNull String>> & I) arg</warning>;
  }

  static class NoStackOverflow {
    public abstract static class Parent
      <S extends Number, B extends Parent<S, B>>{}

    public static class Child extends
                              Parent<Number, Child> {}

    public Parent rawType(Child p) {
      return p;
    }
  }

  @NullMarked
  static class ReturnWithNullMarked {
    static List<@Nullable String> f(List<String> arg) {
      return <warning descr="Returning a class with not-null type arguments when a class with nullable type arguments is expected" tooltip="Returning a class with not-null type arguments when a class with nullable type arguments is expected<table><tr><td>Expected type:</td><td>List<<b>@Nullable</b> String></td></tr><tr><td>Actual type:</td><td>List<<b>@NullMarked</b> String></td></tr></table>">arg</warning>;
    }
  }
}