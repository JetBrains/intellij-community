package org.jetbrains.io.jsonRpc;

public class ExceptionHandlerImpl implements ExceptionHandler {
  @Override
  public void exceptionCaught(Throwable e) {
    //noinspection CallToPrintStackTrace
    e.printStackTrace();
  }
}
