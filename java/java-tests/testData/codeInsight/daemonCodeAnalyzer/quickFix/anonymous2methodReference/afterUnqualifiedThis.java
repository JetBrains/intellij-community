// "Replace with method reference" "true"
class Test {

  private void doTest (){}

  void foo(Runnable r){}

  {
    foo (this::doTest);
  }

}
