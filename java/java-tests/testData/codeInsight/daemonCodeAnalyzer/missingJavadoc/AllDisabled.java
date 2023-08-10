package sample;

public class AllDisabled {

    public int x = 42;
    private int y = 42;

    public int getX(){
      return x;
    }

    public void test1(int param) {
    }

    private String test2(int param) {
      return "42";
    }

    @Deprecated
    public String test3(int param) {
      return "42";
    }

    static class Inner1 {
    }

    private static class Inner2 {
    }
}
