import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Alive {
  @Nullable private transient Object elvis;
  @NotNull
  public Object smth() {
    if (elvis != null) {
      return elvis;
    } else {
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
  }
}