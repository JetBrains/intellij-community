// "Add exception to method signature" "true-preview"

class C {
  interface I { }

  public void localException() throws Exception {

    class Ex extends Exception { }
    class LocalException extends Ex implements I { }

    throw new LocalException();
  }
}
