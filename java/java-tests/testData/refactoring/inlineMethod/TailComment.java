class Test {
  private void b(){
    <caret>a();
  }

  private void a(){
    System.out.println("asdasd");
    //test
  }
}