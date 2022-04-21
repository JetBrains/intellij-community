import org.jetbrains.annotations.NotNull;

class Test {
  public void test() {
    byte[] one = getBytes("one");
    byte[] two = getBytes("two");
  }

    @NotNull
    private static byte[] getBytes(String one) {
        return one.getBytes();
    }
}