class SideEffect {

  public Throwable exception;

  public void checkForException() throws IOException {
    if (exception == null)
      return;
      String message = "An exception occurred" +
              " during the execution of select(): \n" + exception +
              '\n';
    exception = null;
    throw new IOException(message);
  }
}