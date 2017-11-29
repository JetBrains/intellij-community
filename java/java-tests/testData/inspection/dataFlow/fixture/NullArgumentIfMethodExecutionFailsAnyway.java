class Test {
  private static void printAndThrow(String message, Throwable cause) {
    if (message != null) {
      System.err.println(message);
    }
    if (cause != null) {
      cause.printStackTrace();
    }
    throw new IllegalStateException();
  }
  public static void main(String[] args) {
    printAndThrow("test", <warning descr="Passing 'null' argument to non annotated parameter">null</warning>);
    printAndThrow(<warning descr="Passing 'null' argument to non annotated parameter">null</warning>, new NullPointerException());
  }
}
