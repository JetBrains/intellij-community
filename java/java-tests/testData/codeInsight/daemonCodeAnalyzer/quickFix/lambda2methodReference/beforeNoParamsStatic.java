// "Replace lambda with method reference" "true-preview"
class Example {
  public static void m() {
  }

  {
    Runnable r = () -> <caret>m();
  }
}