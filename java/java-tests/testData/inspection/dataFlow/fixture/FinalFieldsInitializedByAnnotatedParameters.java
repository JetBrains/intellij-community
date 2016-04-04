import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.Object;

class Doo {
  private final Object myA;
  private final Object myB;
  private final Object myC;

  public Doo(@Nullable Object myA, @NotNull Object myB, Object c) {
    this.myA = myA;
    this.myB = myB;
    myC = c;
  }

  int bar() {
    return myC.hashCode();
  }


  int foo() {
    if (<warning descr="Condition 'myB != null' is always 'true'">myB != null</warning> &&
    myA.<warning descr="Method invocation 'equals' may produce 'java.lang.NullPointerException'">equals</warning>(myB)) {
      return 2;
    }

    return myA.hashCode();
  }
}
