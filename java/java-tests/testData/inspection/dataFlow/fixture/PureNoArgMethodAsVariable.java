import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// IDEA-141547
public class PureNoArgMethodAsVariable {

  public enum Bar {
    A, B;

    @Nullable
    @Contract(pure = true)
    public String getGroup() {
      return this == A ? null : "B";
    }

    @Nullable
    @Contract(pure = true)
    public String group() {
      return this == A ? null : "B";
    }
  }

  public void foo(Bar bar) {
    if (bar.getGroup() != null && check(bar.getGroup())) { // NO inspection error, OK!
      System.out.print("ok");
    }
    if (bar.group() != null && check(bar.group())) { // Inspection error, NOT OK!
      System.out.print("ok");
    }
  }

  void testIntValue(Integer x) {
    if(<warning descr="Condition 'x.intValue() > 5 && x.intValue() < 0' is always 'false'">x.intValue() > 5 &&
      <warning descr="Condition 'x.intValue() < 0' is always 'false' when reached">x.intValue() < 0</warning></warning>) {
      System.out.println("impossible");
    }
  }

  public boolean check(@NotNull String string) {
    return string.length() > 2;
  }
}