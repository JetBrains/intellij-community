import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class JSpecifyWildcardBoundTypeVariable<T extends @Nullable Object> {
  final Foo<T> foo = new Foo<>();
  final Foo<@Nullable T> nullableFoo = new Foo<>();

  void calls() {
    get1(<warning descr="Incompatible type arguments due to nullability">foo</warning>);
    get2(foo);
    extendsNullable(foo);
    extendsNullable(nullableFoo);
    extendsParametric(foo);
    extendsParametric(<warning descr="Incompatible type arguments due to nullability">nullableFoo</warning>);
    superNullable(<warning descr="Incompatible type arguments due to nullability">foo</warning>);
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
