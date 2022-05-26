package sample;

public class FieldEnabled {

    public int <warning descr="Required Javadoc is absent">x</warning> = 42;
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

    public static class Inner1 {
    }

    private static class Inner2 {
    }
}
