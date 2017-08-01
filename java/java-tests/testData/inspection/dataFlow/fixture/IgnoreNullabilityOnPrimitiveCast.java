import foo.*;

class IgnoreNullabilityOnPrimitiveCast {
  private static int map(@Nullable int[] mapping, int idx) {
    return <warning descr="Array access 'mapping[idx]' may produce 'java.lang.NullPointerException'">mapping[idx]</warning>;
  }
}