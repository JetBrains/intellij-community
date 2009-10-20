class A{
  void foo() throws MyException{
    throw new MyEx<caret>
  }

  class MyException extends Exception{}
}