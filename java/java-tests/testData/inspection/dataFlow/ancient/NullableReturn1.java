import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Test {
  @NotNull Object foo() {
    Object res;
    res = null;
    return <warning descr="Expression 'res' might evaluate to null but is returned by the method declared as @NotNull">res</warning>;
  }
}