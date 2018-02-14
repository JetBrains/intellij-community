// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention;

import org.jetbrains.annotations.NotNull;

/**
 * Marker interface for intentions and quick fixes.
 * Marked actions are shown higher in the list of available quick fixes.
 *
 * @author Dmitry Avdeev
 * @see IntentionAction
 * @see com.intellij.codeInspection.LocalQuickFix
 */
public interface HighPriorityAction extends PriorityAction {

  @NotNull
  @Override
  default Priority getPriority() {
    return Priority.HIGH;
  }
}
