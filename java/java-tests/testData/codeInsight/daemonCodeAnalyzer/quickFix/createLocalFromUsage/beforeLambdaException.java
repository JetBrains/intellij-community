// "Create local variable 'v'" "true-preview"
public class A {
  void foo() {
    L l = () -> {
      throw v<caret>
    }
  }

  interface L {
    void g() throws MyException;
  }
  class MyException extends Exception {}
}
