// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.hosting;

public class RepositoryListLoadingException extends RuntimeException {
  public RepositoryListLoadingException(String message, Throwable cause) {
    super(message, cause);
  }
}
