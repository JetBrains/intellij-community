package packageA;

public class Deri$ved extends Base{
  @Override
  public void foo(String... params) {
    System.out.println("Derived " + params.toString());
  }
}
