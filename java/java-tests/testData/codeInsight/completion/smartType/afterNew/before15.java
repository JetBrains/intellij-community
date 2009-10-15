class A{
  void foo() throws MyException{
    throw new Erro<caret>
  }

  class MyException extends Exception{}
}