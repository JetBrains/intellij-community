public class Test {
  static class Base {
    public void foo() {}
  }

  static class C1 extends Base {
    <caret>protected void foo() {}
  }

  static class C2 extends C1 {
    protected void foo() {}
  }
}