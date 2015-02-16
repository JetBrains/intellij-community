package org.jetbrains.io.jsonRpc;

import org.jetbrains.annotations.NotNull;

public interface ExceptionHandler {
  /**
   * @param e Exception while encode message (on send)
   */
  void exceptionCaught(@NotNull Throwable e);
}
