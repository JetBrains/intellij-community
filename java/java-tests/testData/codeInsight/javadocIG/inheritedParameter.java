public class Outer {
  public static interface Inner {
    /**
     * Method.
     * @param s String parameter.
     */
    void foo(String s);
  }

  public static class Impl implements Inner {
    public void foo(String s) {
    }
  }
}