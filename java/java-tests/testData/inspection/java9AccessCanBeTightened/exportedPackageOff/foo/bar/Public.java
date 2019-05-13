package foo.bar;

public class Public {
  public static class Nested {}
  protected class Inner {}

  private static class Impl extends Public {}
  private static class Impl2 extends Public.Nested {}
  private class Impl3 extends Public.Inner {}
}