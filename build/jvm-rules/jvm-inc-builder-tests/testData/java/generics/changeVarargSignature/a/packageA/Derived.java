package packageA;

public class Derived extends Base{
  @Override
  public void foo(String... params) {
    System.out.println("Derived " + params.toString());
  }
}
