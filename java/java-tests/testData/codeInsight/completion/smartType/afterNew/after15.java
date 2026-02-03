class A{
  void foo() throws MyException{
    throw new Error(<caret>);
  }

  class MyException extends Exception{}
}