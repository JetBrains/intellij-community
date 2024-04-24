import org.jetbrains.annotations.*;

class Test {
  @NotNull
  private static String getKeyText(@NotNull Object key) {
    String res = key.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(key));
    try {
      return res + "(" + key + ")";
    }
    catch (RuntimeException ignored) {
    }
    return res;
  }
}