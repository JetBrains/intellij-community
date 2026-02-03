class MyException extends RuntimeException{}

class MyClass {
public void foo() throws MyException {
  throw new MyEx<caret> 
}
}