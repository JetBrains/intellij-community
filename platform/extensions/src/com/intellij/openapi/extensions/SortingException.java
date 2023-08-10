// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions;

import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;

public final class SortingException extends RuntimeException {
  private final LoadingOrder.Orderable[] myConflictingElements;

  SortingException(String message, LoadingOrder.Orderable @NotNull ... conflictingElements) {
    super(message + ": " + Strings.join(conflictingElements, item -> {
      return item.getOrderId() + "(" + item.getOrder() + ")";
    }, "; "));
    myConflictingElements = conflictingElements;
  }

  public LoadingOrder.Orderable @NotNull [] getConflictingElements() {
    return myConflictingElements;
  }
}
