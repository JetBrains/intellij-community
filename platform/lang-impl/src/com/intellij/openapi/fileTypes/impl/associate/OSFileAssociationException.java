// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl.associate;

public class OSFileAssociationException extends Exception {
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
