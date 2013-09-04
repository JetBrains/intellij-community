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

}

