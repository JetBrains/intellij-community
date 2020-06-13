// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.actions.associate;

public class FileAssociationException extends Exception {
  public FileAssociationException(Throwable cause) {
    super(cause);
  }

  public FileAssociationException(String message) {
    super(message);
  }
}
