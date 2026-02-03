class MyException extends Exception{}

class MyClass {
public void foo() throws MyException {
  throw new MyException();<caret> 
}
}