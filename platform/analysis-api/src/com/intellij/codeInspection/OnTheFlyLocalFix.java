// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

/**
 * A convenient interface to mark fixes not applicable in the batch mode.
 * @deprecated Please manually override availableInBatchMode
 */
@Deprecated(forRemoval = true)
public interface OnTheFlyLocalFix extends LocalQuickFix {
  @Override
  default boolean availableInBatchMode() {
    return false;
  }
}
