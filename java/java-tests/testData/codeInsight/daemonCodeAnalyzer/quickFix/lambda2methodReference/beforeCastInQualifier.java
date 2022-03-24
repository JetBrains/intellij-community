// "Replace lambda with method reference" "false"
class Bar extends Random {
  public void test(Object obj) {
    Runnable r = () -> <caret>((String)obj).trim();
  }
}