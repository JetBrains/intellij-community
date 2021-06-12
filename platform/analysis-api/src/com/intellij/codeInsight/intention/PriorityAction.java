// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    LOW,
    /**
     * Used for the quick fixes ordering in the Alt+Enter popup. With this priority, the corresponding quick fix will be located below the
     * quick fixes from Inspections, even if this quick fix is tied to some ERROR reported by some Annotator whereas Inspections have WARNING level.
     *
     * @see com.intellij.codeInsight.intention.impl.IntentionGroup
     */
    ERROR_FIX_LESS_IMPORTANT_THAN_INSPECTION_FIX
  }

  @NotNull
  Priority getPriority();
}
