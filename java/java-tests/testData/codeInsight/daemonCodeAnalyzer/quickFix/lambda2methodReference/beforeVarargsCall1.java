// "Replace lambda with method reference" "false"
class Example {
  public void m(String ss, String... s) {
  }

  {
    Runnable r = () -> <caret>m("", "");
  }
}