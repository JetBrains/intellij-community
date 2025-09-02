package sample;

public class InnerClassEnabled {

    public int x = 42;
    private int y = 42;

    public int getX(){
      return x;
    }

    public void test1(int param) {
    }

    protected String test2(int param) {
      return "42";
    }

    @Deprecated
    public String test3(int param) {
      return "42";
    }

    public static class <warning descr="Required Javadoc is absent">Inner1</warning> {
    }

    private static class Inner2 {
    }

  <warning descr="Required tag '@param' is missing for parameter 'x'"><warning descr="Required tag '@param' is missing for parameter 'y'">/**</warning></warning>
   * You have a point.
   */
  public record Point(int x, int y) {}
}
