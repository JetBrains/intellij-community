// "Suppress for class" "true"
public class Test {

  public void test() {
    Inner inner = new Inner();
  }

  /** @noinspection deprecation*/
  private static class Inner {

    /**
     * @deprecated
     */
    int i;
    public void unused() {
      i++;
    }
  }
}
