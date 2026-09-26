// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.storage;

import java.io.IOException;

/**
 * This exception indicates that some internal build storage cannot be loaded or saved properly. Rebuild will be requested to recover from
 * the corruption.
 */
public final class BuildDataCorruptedException extends RuntimeException {
  public BuildDataCorruptedException(IOException cause) {
    super(cause);
  }

  public BuildDataCorruptedException(String message) {
    super(message);
  }

  @Override
  public synchronized Throwable initCause(Throwable cause) {
    throw new UnsupportedOperationException("Overwriting of cause field is not supported for " + BuildDataCorruptedException.class.getName());
  }

  @Override
  public synchronized IOException getCause() {
    return (IOException)super.getCause();
  }
}
