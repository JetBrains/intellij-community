package packageA;

public class Derived {
  class Inner extends Base {
    @Override
    public void foo(String... params) {
      System.out.println("Derived " + params.toString());
    }
  }
}
