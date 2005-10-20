/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.application;

public class Result<T> {

  protected T myResult;

  public final void setResult(T result) {
    myResult = result;
  }

}
