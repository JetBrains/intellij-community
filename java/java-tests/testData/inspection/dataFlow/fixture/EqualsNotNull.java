import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Main {
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
    if (val.<warning descr="Method invocation 'equals' may produce 'NullPointerException'">equals</warning>(defVal)) {
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
    if (<warning descr="Condition 'defVal.equals(val)' is always 'false'">defVal.equals(val)</warning>) {
      return val;
    }
    return defVal;
  }

  @NotNull
  private static Object test(@NotNull Object defVal, @Nullable final Object val) {
    if (val != null) {
      return val;
    }
    if (<warning descr="Condition 'defVal == val' is always 'false'">defVal == val</warning>) {
      return val;
    }
    if (<warning descr="Condition 'val == defVal' is always 'false'">val == defVal</warning>) {
      return val;
    }
    if (<warning descr="Condition 'defVal.equals(val)' is always 'false'">defVal.equals(val)</warning>) {
      return val;
    }
    return defVal;
  }

  private static void test2(@NotNull Object defVal, @Nullable final Object val) {
    if(val == defVal) {
      if(<warning descr="Condition 'val == null' is always 'false'">val == null</warning>) {
        System.out.println("oops");
      }
    }
  }

  private void test3(@Nullable Object val) {
    if(<warning descr="Condition 'val != this || val != null' is always 'true'">val != this || <warning descr="Condition 'val != null' is always 'true' when reached">val != null</warning></warning>) {
      System.out.println("never happens");
    }
  }

}