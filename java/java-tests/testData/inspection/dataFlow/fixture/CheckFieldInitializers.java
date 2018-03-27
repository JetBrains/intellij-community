import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


class Foo {
  @Nullable static Object foo() { return null; }
  static String bar(@NotNull Object arg) { return ""; }
}
class Bar {
  public static final String s = Foo.bar(<warning descr="Argument 'Foo.foo()' might be null">Foo.foo()</warning>);
  @NotNull public static Object o = Foo.foo();

}
class Baz {
  @NotNull public static Object o = <warning descr="Expression 'Foo.foo()' might evaluate to null but is assigned to a variable that is annotated with @NotNull">Foo.foo()</warning>;
}