// "Replace with method reference" "true-preview"
class Test {

  private void doTest (){}

  void foo(Runnable r){}

  {
    foo (new Ru<caret>nnable() {
      @Override
      public void run() {
        doTest();
      }
    });
  }

}
