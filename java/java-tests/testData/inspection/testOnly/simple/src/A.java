public class A {
  @org.jetbrains.annotations.TestOnly
  public void test() {}

  public void production() {
   test();
  }
}