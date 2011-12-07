class Test {
  String myT = foo();

  void bar(){
    System.out.println(myT);
  }

  String foo() {
    return "";
  }

  void bazz() {
    bar();
  }
}