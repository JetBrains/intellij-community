public class B {
  public void production() {
    new A().test();
  }

  @org.jetbrains.annotations.TestOnly
  public void test() {
    new A().test();
  }
}