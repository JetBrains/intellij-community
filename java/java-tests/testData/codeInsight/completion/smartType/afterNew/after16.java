class A{
  void foo() throws MyException{
    throw new MyException();<caret>
  }

  class MyException extends Exception{}
}