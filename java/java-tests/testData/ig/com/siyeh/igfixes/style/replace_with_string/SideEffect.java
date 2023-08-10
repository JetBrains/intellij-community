class SideEffect {

  public Throwable exception;

  public void checkForException() throws IOException {
    if (exception == null)
      return;
    StringBuilder <caret>message = new StringBuilder("An exception occurred" +
                                              " during the execution of select(): \n");
    message.append(exception);
    message.append('\n');
    exception = null;
    throw new IOException(message.toString());
  }
}