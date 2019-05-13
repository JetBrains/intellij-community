package org.jetbrains.io.jsonRpc;

import org.jetbrains.annotations.NotNull;

public class ExceptionHandlerImpl implements ExceptionHandler {
  @Override
  public void exceptionCaught(@NotNull Throwable e) {
    //noinspection CallToPrintStackTrace
    e.printStackTrace();
  }
}
