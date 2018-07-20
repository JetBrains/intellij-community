// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention;

import org.jetbrains.annotations.NotNull;

/**
 * Interface for {@link IntentionAction intentions} and {@link com.intellij.codeInspection.LocalQuickFix quick fixes}.
 */
public interface PriorityAction {

  enum Priority {
    HIGH,
    NORMAL,
    LOW
  }

  @NotNull
  Priority getPriority();
}
