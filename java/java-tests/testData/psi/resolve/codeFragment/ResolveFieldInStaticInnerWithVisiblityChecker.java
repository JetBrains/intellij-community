public class FieldVsOuter {
  public final String field = "xxx";

  private static class Inner {
    public final String field = "yyy";

    public void foo() {
      System.out.println(<ref>field);
    }
  }
}