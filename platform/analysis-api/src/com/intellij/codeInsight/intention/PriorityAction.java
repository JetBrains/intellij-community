// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import org.jetbrains.annotations.NotNull;

/**
 * Interface for {@link IntentionAction intentions} and {@link com.intellij.codeInspection.LocalQuickFix quick fixes}.
 */
public interface PriorityAction {

  enum Priority {
    TOP,
    HIGH,
    NORMAL,
    LOW
  }

  @NotNull
  Priority getPriority();
}
