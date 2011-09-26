package packageA;

public class Derived {
  public void bar() {
    Base base = new Base() {
      @Override
      public void foo(String... params) {
        System.out.println("Derived " + params.toString());
      }
    };
  }
}
