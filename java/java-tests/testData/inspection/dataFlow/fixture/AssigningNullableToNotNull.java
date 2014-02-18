import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Npe {
  @NotNull Object aField;
  @Nullable Object nullable() {
    return null;
  }

  void bar() {
    Object o = nullable();
    aField = <warning descr="Expression 'o' might evaluate to null but is assigned to a variable that is annotated with @NotNull">o</warning>;
    @NotNull Object aLocalVariable = o;
  }

  void bar2() {
    Object o = nullable();
    @NotNull Object aLocalVariable = <warning descr="Expression 'o' might evaluate to null but is assigned to a variable that is annotated with @NotNull">o</warning>;
    aField = o;
  }
}