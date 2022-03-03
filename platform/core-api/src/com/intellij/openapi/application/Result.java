// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.util.ThrowableRunnable;

/**
 * @deprecated Use {@link WriteAction#run(ThrowableRunnable)} or {@link ReadAction#run(ThrowableRunnable)} or similar method instead
 */
@Deprecated
public class Result<T> {

  protected T myResult;

  public final void setResult(T result) {
    myResult = result;
  }

}
