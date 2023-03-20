// "Replace with method reference" "true-preview"
class Test {

  private void doTest (){}

  void foo(Runnable r){}

  {
    foo (this::doTest);
  }

}
