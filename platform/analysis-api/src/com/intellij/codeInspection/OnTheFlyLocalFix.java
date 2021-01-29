// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

/**
 * A convenient interface to mark fixes not applicable in the batch mode.
 */
public interface OnTheFlyLocalFix extends LocalQuickFix {
  @Override
  default boolean availableInBatchMode() {
    return false;
  }
}
