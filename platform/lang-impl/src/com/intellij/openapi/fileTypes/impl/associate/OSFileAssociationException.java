// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl.associate;

public final class OSFileAssociationException extends Exception {
  public OSFileAssociationException(Throwable cause) {
    super(cause);
  }

  public OSFileAssociationException(String message) {
    super(message);
  }

  public OSFileAssociationException(String message, Throwable cause) {
    super(message, cause);
  }
}
