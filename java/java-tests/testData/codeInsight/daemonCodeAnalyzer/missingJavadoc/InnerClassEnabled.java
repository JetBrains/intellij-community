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
}
