import org.jspecify.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import java.util.List;
import java.util.Map;
import java.util.Collection;

class B<T> {
  B<@NotNull String> simpleNullableToNotNull(B<@Nullable String> arg) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="<html><body>Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>B&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:219\" style=\"text-decoration: underline\">@NotNull</a></span> String&gt;</td></tr><tr><td>Actual type:</td><td>B&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:262\" style=\"text-decoration: underline\">@Nullable</a></span> String&gt;</td></tr></table></body></html>">arg</warning>;
  }

  B<@Nullable String> simpleNotNullToNullable(B<@NotNull String> arg) {
    return <warning descr="Returning a class with not-null type arguments when a class with nullable type arguments is expected" tooltip="<html><body>Returning a class with not-null type arguments when a class with nullable type arguments is expected<table><tr><td>Expected type:</td><td>B&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:312\" style=\"text-decoration: underline\">@Nullable</a></span> String&gt;</td></tr><tr><td>Actual type:</td><td>B&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:356\" style=\"text-decoration: underline\">@NotNull</a></span> String&gt;</td></tr></table></body></html>">arg</warning>;
  }

  B<B<@NotNull String>> nested(B<B<@Nullable String>> arg) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="<html><body>Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>B&lt;B&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:407\" style=\"text-decoration: underline\">@NotNull</a></span> String&gt;&gt;</td></tr><tr><td>Actual type:</td><td>B&lt;B&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:436\" style=\"text-decoration: underline\">@Nullable</a></span> String&gt;&gt;</td></tr></table></body></html>">arg</warning>;
  }

  B<? extends @Nullable Object> extendsWildcardNullable(B<@NotNull Object> arg) {
    return arg;
  }

  B<? extends @NotNull Object> extendsWildcardNotNull(B<@Nullable String> arg) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="<html><body>Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>B&lt;? extends <span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:600\" style=\"text-decoration: underline\">@NotNull</a></span> Object&gt;</td></tr><tr><td>Actual type:</td><td>B&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:642\" style=\"text-decoration: underline\">@Nullable</a></span> String&gt;</td></tr></table></body></html>">arg</warning>;
  }

  B<? super @NotNull String> SupperWildcard(B<@Nullable Object> arg) {
    return arg;
  }

  B<? extends @NotNull Object> extendsWildcardBothNotNull(B<? extends @Nullable Object> arg) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="<html><body>Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>B&lt;? extends <span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:794\" style=\"text-decoration: underline\">@NotNull</a></span> Object&gt;</td></tr><tr><td>Actual type:</td><td>B&lt;? extends <span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:850\" style=\"text-decoration: underline\">@Nullable</a></span> Object&gt;</td></tr></table></body></html>">arg</warning>;
  }

  B<? extends @Nullable Object> extendsWildcardBothNullable(B<? extends @NotNull Object> arg) {
    return arg;
  }

  B<? super @NotNull Object> superWildCardBoth(B<? super @Nullable Object> arg) {
    return arg;
  }

  B<? extends B<@NotNull Object>> nestedWithWildcard(B<B<@Nullable Object>> arg) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="<html><body>Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>B&lt;? extends B&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:1132\" style=\"text-decoration: underline\">@NotNull</a></span> Object&gt;&gt;</td></tr><tr><td>Actual type:</td><td>B&lt;B&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:1173\" style=\"text-decoration: underline\">@Nullable</a></span> Object&gt;&gt;</td></tr></table></body></html>">arg</warning>;
  }

  Map<List<String>, List<@Nullable String>> checkIsPerformedIfSecondTypeArgumentIsTheSame(Map<List<String>, List<@NotNull String>> arg) {
    return <warning descr="Returning a class with not-null type arguments when a class with nullable type arguments is expected" tooltip="<html><body>Returning a class with not-null type arguments when a class with nullable type arguments is expected<table><tr><td>Expected type:</td><td>Map&lt;List&lt;String&gt;, List&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:1245\" style=\"text-decoration: underline\">@Nullable</a></span> String&gt;&gt;</td></tr><tr><td>Actual type:</td><td>Map&lt;List&lt;String&gt;, List&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:1333\" style=\"text-decoration: underline\">@NotNull</a></span> String&gt;&gt;</td></tr></table></body></html>">arg</warning>;
  }

  B<@NotNull String>[] array(B<@Nullable String>[] arg) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="<html><body>Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>B&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:1383\" style=\"text-decoration: underline\">@NotNull</a></span> String&gt;[]</td></tr><tr><td>Actual type:</td><td>B&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:1410\" style=\"text-decoration: underline\">@Nullable</a></span> String&gt;[]</td></tr></table></body></html>">arg</warning>;
  }

  Object[] @NotNull [] nullabilityInNestedArray(Object[] @Nullable [] arg) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="<html><body>Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>Object[] <span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:1469\" style=\"text-decoration: underline\">@NotNull</a></span> []</td></tr><tr><td>Actual type:</td><td>Object[] <span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:1515\" style=\"text-decoration: underline\">@Nullable</a></span> []</td></tr></table></body></html>">arg</warning>;
  }

  B<@NotNull String>[][] multiDimensionalArray(B<@Nullable String>[][] arg) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="<html><body>Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>B&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:1560\" style=\"text-decoration: underline\">@NotNull</a></span> String&gt;[][]</td></tr><tr><td>Actual type:</td><td>B&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:1605\" style=\"text-decoration: underline\">@Nullable</a></span> String&gt;[][]</td></tr></table></body></html>">arg</warning>;
  }

  static class C<T, V> {
    C<Object, @NotNull String> secondArgument(C<Object, @Nullable String> arg) {
      return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected" tooltip="<html><body>Returning a class with nullable type arguments when a class with not-null type arguments is expected<table><tr><td>Expected type:</td><td>C&lt;Object, <span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:1694\" style=\"text-decoration: underline\">@NotNull</a></span> String&gt;</td></tr><tr><td>Actual type:</td><td>C&lt;Object, <span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:1736\" style=\"text-decoration: underline\">@Nullable</a></span> String&gt;</td></tr></table></body></html>">arg</warning>;
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
      return <warning descr="Returning a class with not-null type arguments when a class with nullable type arguments is expected" tooltip="<html><body>Returning a class with not-null type arguments when a class with nullable type arguments is expected<table><tr><td>Expected type:</td><td>List&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:2703\" style=\"text-decoration: underline\">@Nullable</a></span> String&gt;</td></tr><tr><td>Actual type:</td><td>List&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/ReturnIncompatibilitiesWithGeneric.java:2637\" style=\"text-decoration: underline\">@NullMarked</a></span> String&gt;</td></tr></table></body></html>">arg</warning>;
    }
  }
}
