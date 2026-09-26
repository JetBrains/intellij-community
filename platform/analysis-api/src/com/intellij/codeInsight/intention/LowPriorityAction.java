// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import org.jetbrains.annotations.NotNull;

/**
 * Marker interface for intentions and quick fixes.
 * Marked actions are shown lower in the list of available quick fixes.
 * Note that this marker interface is not intended for {@link ModCommandAction}.
 * You can implement {@link ModCommandAction} and its {@link ModCommandAction#getPresentation(ActionContext)}
 * may use {@link Presentation#withPriority(PriorityAction.Priority)}.
 *
 * @author Max Ishchenko
 */
public interface LowPriorityAction extends PriorityAction {

  @Override
  default @NotNull Priority getPriority() {
    return Priority.LOW;
  }
}
