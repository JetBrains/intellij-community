import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Foo {

  public void main(@NotNull Object nn) {
    foo(nn).hashCode();
  }

  @Contract("null->null;!null->!null")
  @Nullable
  Object foo(Object a) { return a; }

  @NotNull
  Object notNull() {
    return <warning descr="Expression 'nullable(false)' might evaluate to null but is returned by the method declared as @NotNull">nullable(false)</warning>;
  }

  @NotNull
  Object notNull2() {
    return <warning descr="Expression 'nullable2(false)' might evaluate to null but is returned by the method declared as @NotNull">nullable2(false)</warning>;
  }

  @Nullable
  @Contract("true -> !null")
  Object nullable(boolean notNull) {
    return notNull ? "" : anotherNullable();
  }

  @Nullable
  @Contract("true -> !null; _->_")
  Object nullable2(boolean notNull) {
    return notNull ? "" : anotherNullable();
  }

  @Nullable
  Object anotherNullable() {
    return null;
  }

}

