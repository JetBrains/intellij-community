// "Replace with lambda" "true"
class Test {
  Integer s;
  private void m() {
    Runnable r = () -> {
      System.out.println(s);
      Integer s = Test.this.s;
    };
  }
}