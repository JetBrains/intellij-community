class MyException extends RuntimeException{}

class MyClass {
/**
* @throws MyException <caret>
*/
public void foo() throws MyException {
}
}