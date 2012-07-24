import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Main {
  @NotNull
  private Object test1(@NotNull Object defVal, @Nullable final Object val) {
    return defVal;
  }
  @NotNull
  private Object test11(@NotNull Object defVal, @Nullable final Object val) {
    if (val != null) {
      return val;
    }
    return defVal;
  }
  @NotNull
  private Object test5(@NotNull Object defVal, @Nullable final Object val) {
    if (defVal == val) {
      return val;
    }
    return defVal;
  }
  @NotNull
  private Object test6(@NotNull Object defVal, @Nullable final Object val) {
    if (val == defVal) {
      return val;
    }
    return defVal;
  }
  @NotNull
  private Object test7(@NotNull Object defVal, @Nullable final Object val) {
    if (<warning descr="Method invocation 'val.equals(defVal)' may produce 'java.lang.NullPointerException'">val.equals(defVal)</warning>) {
      return defVal;
    }
    return defVal;
  }
  @NotNull
  private Object test8(@NotNull Object defVal, @Nullable final Object val) {
    if (defVal.equals(val)) {
      return val;
    }
    return defVal;
  }
  @NotNull private Object test9(@NotNull Object defVal, @Nullable final Object val) {
    if (equals(val)) {
      return val;
    }
    return defVal;
  }
  @NotNull private Object test10(@NotNull Object defVal, @Nullable final Object val) {
    if (val != null) {
      return val;
    }
    if (defVal.equals(val)) {
      return <warning descr="Expression 'val' might evaluate to null but is returned by the method declared as @NotNull">val</warning>;
    }
    return defVal;
  }
}