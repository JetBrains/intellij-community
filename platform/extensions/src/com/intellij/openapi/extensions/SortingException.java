// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public final class SortingException extends RuntimeException {
  private final LoadingOrder.Orderable[] myConflictingElements;

  SortingException(String message, LoadingOrder.Orderable @NotNull ... conflictingElements) {
    super(message + ": " + StringUtil.join(conflictingElements, item -> {
      return item.getOrderId() + "(" + item.getOrder() + ")";
    }, "; "));
    myConflictingElements = conflictingElements;
  }

  public LoadingOrder.Orderable @NotNull [] getConflictingElements() {
    return myConflictingElements;
  }
}
