import org.jetbrains.annotations.Nullable;

class Test {

  @Nullable
  private static native String getNext();

  public static void test() {
    String v = null;
    while ((v = getNext()) != null) {
      v.substring(1);
    }
  }
}