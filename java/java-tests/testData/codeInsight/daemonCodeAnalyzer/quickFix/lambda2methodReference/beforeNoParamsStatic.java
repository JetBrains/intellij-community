// "Replace lambda with method reference" "true"
class Example {
  public static void m() {
  }

  {
    Runnable r = () -> <caret>m();
  }
}