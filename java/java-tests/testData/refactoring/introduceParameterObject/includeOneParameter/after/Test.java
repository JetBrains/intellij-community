public class Test {
  public void foo(int a, Param param) {}

  {
    foo(1, new Param(""));
  }
}