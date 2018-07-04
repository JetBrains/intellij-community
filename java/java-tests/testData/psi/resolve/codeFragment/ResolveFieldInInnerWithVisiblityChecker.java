public class FieldVsOuter2 {
  public final String field = "xxx";

  private class Inner {
    public final String field = "yyy";

    public void foo() {
      System.out.println(<ref>field);
    }
  }
}