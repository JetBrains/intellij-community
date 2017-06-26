package foo.bar;

public class Outer {
  public static class Nested {}
  protected class Inner {}

  private static class Outer2 extends Outer.Nested {}
  private class Outer3 extends Outer.Inner {}
}