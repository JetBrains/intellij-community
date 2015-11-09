class FirstException extends Exception {}
class SecondException extends Exception {}

interface I {
  void method() throws FirstException, SecondException;
}

class C implements I {
  void method() throws FirstException, Sec<caret>;
}