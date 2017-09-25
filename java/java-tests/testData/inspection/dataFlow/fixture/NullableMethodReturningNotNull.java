import org.jetbrains.annotations.*;

final class Foo {
  @<warning descr="@Nullable method 'foo' always returns a non-null value">Nullable</warning> Object foo(int param) {
    return param == 1 ? new Object() {
      Object unrelated() {
        return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>;
      }
    } : bar();
  }

  @NotNull Foo bar() {
    return this;
  }

  @Nullable
  Object throwing() {
    throw new UnsupportedOperationException();
  }
}
