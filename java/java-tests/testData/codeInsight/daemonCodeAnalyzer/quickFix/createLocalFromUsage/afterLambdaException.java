// "Create local variable 'v'" "true-preview"
public class A {
  void foo() {
    L l = () -> {
        MyException v;
        throw v
    }
  }

  interface L {
    void g() throws MyException;
  }
  class MyException extends Exception {}
}
