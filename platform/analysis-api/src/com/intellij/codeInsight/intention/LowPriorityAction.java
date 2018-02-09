// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Marker interface for intentions and quick fixes.
 * Marked actions are shown lower in the list of available quick fixes.
 *
 * @author Max Ishchenko
 */
public interface LowPriorityAction extends PriorityAction {

  @Override
  default int getPriorityModifier(@NotNull Project project) {
    return LOWER_PRIORITY;
  }
}
