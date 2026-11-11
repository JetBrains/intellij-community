import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class JSpecifyWildcardBoundTypeVariable<T extends @Nullable Object> {
  final Foo<T> foo = new Foo<>();
  final Foo<@Nullable T> nullableFoo = new Foo<>();

  void calls() {
    get1(<warning descr="Incompatible type arguments due to nullability" tooltip="<html><body>Incompatible type arguments due to nullability<table><tr><td>Expected type:</td><td>Foo&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/JSpecifyWildcardBoundTypeVariable.java:789\" style=\"text-decoration: underline\">@Nullable</a></span> T&gt;</td></tr><tr><td>Actual type:</td><td>Foo&lt;T&gt;</td></tr><tr><td colspan=\"2\">&#39;T&#39; in the actual type can be instantiated as non-null</td></tr></table></body></html>">foo</warning>);
    get2(foo);
    extendsNullable(foo);
    extendsNullable(nullableFoo);
    extendsParametric(foo);
    extendsParametric(<warning descr="Incompatible type arguments due to nullability" tooltip="<html><body>Incompatible type arguments due to nullability<table><tr><td>Expected type:</td><td>Foo&lt;? extends T&gt;</td></tr><tr><td>Actual type:</td><td>Foo&lt;<span style=\"color: #c7222d\"><a href=\"#navigation//src/JSpecifyWildcardBoundTypeVariable.java:215\" style=\"text-decoration: underline\">@Nullable</a></span> T&gt;</td></tr><tr><td colspan=\"2\">&#39;T&#39; in the expected type can be instantiated as non-null</td></tr></table></body></html>">nullableFoo</warning>);
    superNullable(<warning descr="Incompatible type arguments due to nullability" tooltip="<html><body>Incompatible type arguments due to nullability<table><tr><td>Expected type:</td><td>Foo&lt;? super <span style=\"color: #c7222d\"><a href=\"#navigation//src/JSpecifyWildcardBoundTypeVariable.java:691\" style=\"text-decoration: underline\">@Nullable</a></span> T&gt;</td></tr><tr><td>Actual type:</td><td>Foo&lt;T&gt;</td></tr><tr><td colspan=\"2\">&#39;T&#39; in the actual type can be instantiated as non-null</td></tr></table></body></html>">foo</warning>);
    superNullable(nullableFoo);
    superParametric(foo);
    superParametric(nullableFoo);
  }

  void extendsNullable(Foo<? extends @Nullable T> foo) {}

  void extendsParametric(Foo<? extends T> foo) {}

  void superNullable(Foo<? super @Nullable T> foo) {}

  void superParametric(Foo<? super T> foo) {}

  static <T> Bar<T> get1(Foo<@Nullable T> foo) {
    return new Bar<>() {
    };
  }

  static <T> Bar<T> get2(Foo<? extends @Nullable T> foo) {
    return new Bar<>() {
    };
  }

  static class Foo<T extends @Nullable Object> {
  }

  interface Bar<T> {
  }
}
