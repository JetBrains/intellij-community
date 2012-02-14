public interface A {
  private static void foo(String s) {}
  void bar() {
    foo("");
  }
}