/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution;

public class ExecutionException extends Exception {
  public ExecutionException(final String s) {
    super(s);
  }

  public ExecutionException(final String s, Throwable cause) {
    super(s, cause);
  }
}