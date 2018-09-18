// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Kireyev
 */
public class SortingException extends RuntimeException {
  private final LoadingOrder.Orderable[] myConflictingElements;

  SortingException(String message, @NotNull LoadingOrder.Orderable... conflictingElements) {
    super(message + ": " + StringUtil.join(conflictingElements,
                                           item -> item.getOrderId() + "(" + item.getOrder() + ")", "; "));
    myConflictingElements = conflictingElements;
  }

  @NotNull
  public LoadingOrder.Orderable[] getConflictingElements() {
    return myConflictingElements;
  }
}
