package sample;

public class PublicMemberInPrivateClass {

    public int <warning descr="Required Javadoc is absent">x</warning> = 42;
    public String <warning descr="Required Javadoc is absent">test1</warning>(int param) {
      return "42";
    }

    private static class Inner2 {
      public int y = 42;
      public String test2(int param) {
        return "42";
      }
    }
}