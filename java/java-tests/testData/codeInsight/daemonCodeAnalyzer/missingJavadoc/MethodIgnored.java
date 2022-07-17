package sample;

public class MethodIgnored {

    public int x = 42;
    private int y = 42;

    public int getX(){
      return x;
    }

    public void <warning descr="Required Javadoc is absent">test1</warning>(int param) {
    }

    protected String <warning descr="Required Javadoc is absent">test2</warning>(int param) {
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
