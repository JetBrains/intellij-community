class MyException extends RuntimeException {
  public MyException(String[] s) {}
}

class XXX {
  {
    throw new MyException(new String[<caret>]);
  }
}