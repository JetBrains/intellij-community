import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Doo {
  private final Object myA;
  private final Object myB;

  public Doo(@Nullable Object myA, @NotNull Object myB) {
    this.myA = myA;
    this.myB = myB;
  }

  int foo() {
    if (<warning descr="Condition 'myB != null' is always 'true'">myB != null</warning> &&
    <warning descr="Method invocation 'myA.equals(myB)' may produce 'java.lang.NullPointerException'">myA.equals(myB)</warning>) {
      return 2;
    }

    return myA.hashCode();
  }
}
